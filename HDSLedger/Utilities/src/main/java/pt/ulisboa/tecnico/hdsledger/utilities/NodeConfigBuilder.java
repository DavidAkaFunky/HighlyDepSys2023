package pt.ulisboa.tecnico.hdsledger.utilities;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class NodeConfigBuilder {

    private final NodeConfig instance = new NodeConfig();

    public NodeConfigBuilder setHostname(String hostname) {
        instance.setHostname(hostname);
        return this;
    }

    public NodeConfigBuilder setLeader(boolean isLeader) {
        instance.setLeader(isLeader);
        return this;
    }

    public NodeConfigBuilder setPort(int port) {
        instance.setPort(port);
        return this;
    }

    public NodeConfigBuilder setId(String id) {
        instance.setId(id);
        return this;
    }

    public NodeConfigBuilder setClientPort(int port) {
        instance.setClientPort(port);
        return this;
    }

    public NodeConfig build() {
        return instance;
    }

    public NodeConfig[] fromFile(String path) {
        try (BufferedInputStream is = new BufferedInputStream(new FileInputStream(path))) {
            String input = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            Gson gson = new Gson();
            return gson.fromJson(input, NodeConfig[].class);
        } catch (FileNotFoundException e) {
            throw new LedgerException(ErrorMessage.ConfigFileNotFound);
        } catch (IOException | JsonSyntaxException e) {
            throw new LedgerException(ErrorMessage.ConfigFileFormat);
        }
    }

}
