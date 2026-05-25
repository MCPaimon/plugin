package io.github.mcpaimon.common;

import io.github.mcpaimon.api.database.IAIDatabase;
import io.github.mcpaimon.api.model.*;
import io.github.mcpaimon.api.tools.AITool;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.*;

/**
 * Manages the core AI functionalities including platforms, models, accounts, active sessions, tools, and categories.
 * API Tokens are automatically encrypted before being stored in the database using AES/CBC with a random IV.
 */
public class MCAIManager {
    /**
     * The database implementation used for storing AI data.
     */
    private final IAIDatabase database;
    
    /**
     * A thread-safe map storing registered AI tools by their names.
     */
    private final Map<String, AITool> registeredTools = new ConcurrentHashMap<>();
    
    /**
     * A thread-safe map storing registered tool categories and their descriptions.
     */
    private final Map<String, String> registeredCategories = new ConcurrentHashMap<>();

    /**
     * The AES secret key specification used for encrypting and decrypting tokens.
     */
    private final SecretKeySpec secretKey;

    /**
     * Secure random instance for generating Initialization Vectors (IV).
     */
    private final SecureRandom secureRandom;

    /**
     * Constructs a new MCAIManager instance.
     *
     * @param database    The database implementation used for storing AI data.
     * @param tokenSecret The secret key string used for encryption.
     */
    public MCAIManager(IAIDatabase database, String tokenSecret) {
        this.database = database;
        this.secretKey = generateEncryptionKey(tokenSecret);
        this.secureRandom = new SecureRandom();
    }

    /**
     * Generates a valid AES 256-bit SecretKeySpec from the provided string using SHA-256 hash.
     */
    private SecretKeySpec generateEncryptionKey(String secret) {
        try {
            if (secret == null || secret.isBlank()) {
                secret = "default_fallback_secret_do_not_use_in_prod";
            }
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            byte[] key = sha.digest(secret.getBytes(StandardCharsets.UTF_8));
            return new SecretKeySpec(key, "AES");
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate encryption key", e);
        }
    }

    /**
     * Encrypts the provided string using AES/CBC/PKCS5Padding with a random IV.
     * The result is a Base64 string containing both the IV and the ciphertext.
     */
    private String encrypt(String strToEncrypt) {
        if (strToEncrypt == null || strToEncrypt.isBlank()) return strToEncrypt;
        try {
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            byte[] iv = new byte[16];
            secureRandom.nextBytes(iv);
            IvParameterSpec ivParameterSpec = new IvParameterSpec(iv);
            
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, ivParameterSpec);
            byte[] encrypted = cipher.doFinal(strToEncrypt.getBytes(StandardCharsets.UTF_8));
            
            // Combine IV and encrypted data
            byte[] combined = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);
            
            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            System.err.println("[MCAI] Error while encrypting token: " + e.toString());
            return strToEncrypt;
        }
    }

    /**
     * Decrypts the provided string using AES/CBC/PKCS5Padding.
     * Extracts the IV from the first 16 bytes of the decoded Base64 string.
     */
    private String decrypt(String strToDecrypt) {
        if (strToDecrypt == null || strToDecrypt.isBlank()) return strToDecrypt;
        try {
            byte[] decoded = Base64.getDecoder().decode(strToDecrypt);
            
            if (decoded.length < 16) {
                return strToDecrypt; // Not long enough to contain an IV, likely a plaintext fallback
            }
            
            byte[] iv = new byte[16];
            System.arraycopy(decoded, 0, iv, 0, 16);
            IvParameterSpec ivParameterSpec = new IvParameterSpec(iv);
            
            byte[] encrypted = new byte[decoded.length - 16];
            System.arraycopy(decoded, 16, encrypted, 0, encrypted.length);
            
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, secretKey, ivParameterSpec);
            
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception e) {
            // Failsafe: return original string if decryption fails (e.g., legacy plain text token)
            return strToDecrypt;
        }
    }

    /**
     * Registers a new AI platform in the database.
     *
     * @param displayName The display name of the AI platform.
     * @param url         The base URL of the AI platform API.
     * @return A CompletableFuture containing the newly created AIPlatform object.
     */
    public CompletableFuture<AIPlatform> registerPlatform(String displayName, String url) { 
        return this.database.createPlatform(displayName, url); 
    }

    /**
     * Registers a new AI model under a specific platform.
     *
     * @param platformId The ID of the platform to associate the model with.
     * @param modelId    The identifier of the AI model.
     * @return A CompletableFuture containing the newly created AIModel object.
     */
    public CompletableFuture<AIModel> registerModel(int platformId, String modelId) { 
        return this.database.createModel(platformId, modelId); 
    }
    
    /**
     * Creates or updates an AI account for a specific user and platform.
     * The token is securely encrypted before storage.
     *
     * @param accountType The type of the account (e.g., "player").
     * @param accountUuid The unique identifier of the account owner.
     * @param platformId  The ID of the associated platform.
     * @param token       The API token for the platform.
     * @return A CompletableFuture containing the updated or created AIAccount object.
     */
    public CompletableFuture<AIAccount> setupAccount(String accountType, String accountUuid, int platformId, String token) { 
        String encryptedToken = encrypt(token);
        return this.database.createOrUpdateAccount(accountType, accountUuid, platformId, encryptedToken)
            .thenApply(acc -> new AIAccount(
                acc.accountType(), 
                acc.accountUuid(), 
                acc.platformId(), 
                decrypt(acc.token()), 
                acc.createdAt(), 
                acc.updatedAt()
            )); 
    }
    
    /**
     * Fetches an existing AI account from the database and decrypts its token.
     *
     * @param accountType The type of the account.
     * @param accountUuid The unique identifier of the account owner.
     * @param platformId  The ID of the associated platform.
     * @return A CompletableFuture containing an Optional AIAccount.
     */
    public CompletableFuture<Optional<AIAccount>> fetchAccount(String accountType, String accountUuid, int platformId) { 
        return this.database.getAccount(accountType, accountUuid, platformId).thenApply(opt -> {
            if (opt.isPresent()) {
                AIAccount acc = opt.get();
                return Optional.of(new AIAccount(
                    acc.accountType(), 
                    acc.accountUuid(), 
                    acc.platformId(), 
                    decrypt(acc.token()), 
                    acc.createdAt(), 
                    acc.updatedAt()
                ));
            }
            return opt;
        });
    }

    /**
     * Sets the active AI session for a specific account.
     *
     * @param accountType The type of the account.
     * @param accountUuid The unique identifier of the account owner.
     * @param platformId  The ID of the platform to be used.
     * @param modelId     The identifier of the model to be used.
     * @return A CompletableFuture representing the completion of the operation.
     */
    public CompletableFuture<Void> setActiveSession(String accountType, String accountUuid, int platformId, String modelId) {
        return this.database.setActiveSession(accountType, accountUuid, platformId, modelId);
    }

    /**
     * Retrieves the current active AI session for an account.
     *
     * @param accountType The type of the account.
     * @param accountUuid The unique identifier of the account owner.
     * @return A CompletableFuture containing an Optional AIActiveSession.
     */
    public CompletableFuture<Optional<AIActiveSession>> getActiveSession(String accountType, String accountUuid) {
        return this.database.getActiveSession(accountType, accountUuid);
    }

    /**
     * Retrieves all registered AI platforms from the database.
     *
     * @return A CompletableFuture containing a list of all platforms.
     */
    public CompletableFuture<List<AIPlatform>> getAllPlatforms() {
        return this.database.getAllPlatforms();
    }

    /**
     * Creates and registers a new tool category. 
     * Prevents duplicate registration and prints a warning message if it already exists.
     *
     * @param categoryId  The unique identifier of the category.
     * @param description A brief description of the category.
     */
    public void createCategory(String categoryId, String description) {
        if (this.registeredCategories.containsKey(categoryId)) {
            System.out.println("This category " + categoryId + " is registered");
            return;
        }
        this.registeredCategories.put(categoryId, description);
    }

    /**
     * Retrieves all registered tool categories.
     *
     * @return A copy of the map containing all category IDs and their descriptions.
     */
    public Map<String, String> getAllCategories() {
        return new HashMap<>(this.registeredCategories);
    }

    /**
     * Registers a new AI tool.
     * Prevents duplicate registration and prints a warning message if the tool name already exists.
     *
     * @param tool The AITool instance to be registered.
     */
    public void registerTool(AITool tool) {
        String toolName = tool.getName();
        
        if (this.registeredTools.containsKey(toolName)) {
            System.out.println("[MCAI Warning] Registration skipped: tool '" + toolName + "' is already registered.");
            return;
        }
        
        this.registeredTools.put(toolName, tool); 
    }

    /**
     * Retrieves all registered AI tools.
     *
     * @return A list of all registered AITool instances.
     */
    public List<AITool> getAllRegisteredTools() { return new ArrayList<>(this.registeredTools.values()); }
    
    /**
     * Executes a specific AI tool by its name with the provided arguments.
     *
     * @param toolName  The name of the tool to execute.
     * @param arguments The arguments to pass to the tool.
     * @param account   The account initiating the tool execution.
     * @return A CompletableFuture containing the string result of the execution.
     */
    public CompletableFuture<String> executeToolCall(String toolName, Map<String, Object> arguments, AIAccount account) {
        AITool tool = registeredTools.get(toolName);
        if (tool == null) {
            return CompletableFuture.completedFuture("Error: Unknown Tool '" + toolName + "'");
        }
        return tool.execute(arguments, account);
    }
}
