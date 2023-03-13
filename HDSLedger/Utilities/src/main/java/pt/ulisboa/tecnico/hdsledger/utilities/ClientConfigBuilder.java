package pt.ulisboa.tecnico.hdsledger.utilities;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class ClientConfigBuilder {
    private final ClientConfig instance = new ClientConfig();

    public ClientConfigBuilder setId(String id) {
        instance.setId(id);
        return this;
    }

    public ClientConfigBuilder setPublicKeyPath(String path) {
        instance.setPublicKeyPath(path);
        return this;
    }

    public ClientConfigBuilder setPrivateKeyPath(String path) {
        instance.setPrivateKeyPath(path);
        return this;
    }

    public ClientConfig build() {
        return instance;
    }
    public ClientConfig[] fromFile(String path) {
        try (BufferedInputStream is = new BufferedInputStream(new FileInputStream(path))) {
            String input = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            Gson gson = new Gson();
            return gson.fromJson(input, ClientConfig[].class);
        } catch (FileNotFoundException e) {
            throw new LedgerException(ErrorMessage.ConfigFileNotFound);
        } catch (IOException | JsonSyntaxException e) {
            throw new LedgerException(ErrorMessage.ConfigFileFormat);
        }
    }

}
