package pt.ulisboa.tecnico.hdsledger.utilities;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

public class RSAEncryption {

    private static byte[] readFile(String path) throws FileNotFoundException, IOException {

        FileInputStream fis = new FileInputStream(path);
        byte[] content = new byte[fis.available()];
        fis.read(content);
        fis.close();

        return content;
    }

    public static String encodePublicKey(PublicKey key) {
        return Base64.getEncoder().encodeToString(key.getEncoded());
    }

    public static PublicKey decodePublicKey(String key) {
        byte[] keyBytes = Base64.getDecoder().decode(key);
        try {
            return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(keyBytes));
        } catch(Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static PublicKey readPublicKey(String publicKeyPath)
            throws FileNotFoundException, IOException, NoSuchAlgorithmException, InvalidKeySpecException {

        byte[] pubEncoded = readFile(publicKeyPath);
        X509EncodedKeySpec pubSpec = new X509EncodedKeySpec(pubEncoded);
        KeyFactory keyFacPub = KeyFactory.getInstance("RSA");
        PublicKey pub = keyFacPub.generatePublic(pubSpec);

        return pub;
    }

    public static PrivateKey readPrivateKey(String privateKeyPath)
            throws FileNotFoundException, IOException, NoSuchAlgorithmException, InvalidKeySpecException {

        byte[] privEncoded = readFile(privateKeyPath);
        PKCS8EncodedKeySpec privSpec = new PKCS8EncodedKeySpec(privEncoded);
        KeyFactory keyFacPriv = KeyFactory.getInstance("RSA");
        PrivateKey priv = keyFacPriv.generatePrivate(privSpec);

        return priv;
    }

    public static byte[] encrypt(byte[] data, String pathToPrivateKey)
            throws FileNotFoundException, NoSuchAlgorithmException, InvalidKeySpecException, IOException,
            NoSuchPaddingException, InvalidKeyException, IllegalBlockSizeException, BadPaddingException {

        PrivateKey privateKey = readPrivateKey(pathToPrivateKey);
        Cipher encryptCipher = Cipher.getInstance("RSA");
        encryptCipher.init(Cipher.ENCRYPT_MODE, privateKey);
        byte[] encryptedData = encryptCipher.doFinal(data);

        return encryptedData;
    }

    public static byte[] decrypt(byte[] data, String pathToPublicKey)
            throws FileNotFoundException, NoSuchAlgorithmException, InvalidKeySpecException, IOException,
            NoSuchPaddingException, InvalidKeyException, IllegalBlockSizeException, BadPaddingException {

        PublicKey publicKey = readPublicKey(pathToPublicKey);
        Cipher decryptCipher = Cipher.getInstance("RSA");
        decryptCipher.init(Cipher.DECRYPT_MODE, publicKey);
        byte[] decryptedData = decryptCipher.doFinal(data);

        return decryptedData;
    }

    public static String digest(String data) throws NoSuchAlgorithmException {
        byte[] dataBytes = data.getBytes();
        final String DIGEST_ALGO = "SHA-256";
        MessageDigest messageDigest = MessageDigest.getInstance(DIGEST_ALGO);
        messageDigest.update(dataBytes);
        byte[] digestBytes = messageDigest.digest();

        return Base64.getEncoder().encodeToString(digestBytes);
    }

    public static String sign(String data, String pathToPrivateKey)
            throws NoSuchAlgorithmException, InvalidKeyException, FileNotFoundException, InvalidKeySpecException,
            NoSuchPaddingException, IllegalBlockSizeException, BadPaddingException, IOException {

        String digest = digest(data);
        byte[] digestEncrypted = encrypt(digest.getBytes(), pathToPrivateKey);
        String digestBase64 = Base64.getEncoder().encodeToString(digestEncrypted);

        return digestBase64;
    }

    public static boolean verifySignature(String data, String signature, String pathToPublicKey) {
        try {
            String hash = digest(data);
            byte[] signatureBytes = Base64.getDecoder().decode(signature);
            String decryptedHash = new String(decrypt(signatureBytes, pathToPublicKey));
            return hash.equals(decryptedHash);

        } catch (Exception e) {
            return false;
        }
    }
}
