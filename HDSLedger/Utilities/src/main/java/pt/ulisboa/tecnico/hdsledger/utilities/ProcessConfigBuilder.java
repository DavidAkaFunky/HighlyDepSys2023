package pt.ulisboa.tecnico.hdsledger.utilities;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class ProcessConfigBuilder {

    private final ProcessConfig instance = new ProcessConfig();

    public ProcessConfigBuilder setHostname(String hostname) {
        instance.setHostname(hostname);
        return this;
    }

    public ProcessConfigBuilder setLeader(boolean isLeader) {
        instance.setLeader(isLeader);
        return this;
    }

    public ProcessConfigBuilder setPort(int port) {
        instance.setPort(port);
        return this;
    }

    public ProcessConfigBuilder setId(String id) {
        instance.setId(id);
        return this;
    }

    public ProcessConfigBuilder setClientPort(int port) {
        instance.setClientPort(port);
        return this;
    }

    public ProcessConfig build() {
        return instance;
    }

    public ProcessConfig[] fromFile(String path) {
        try (BufferedInputStream is = new BufferedInputStream(new FileInputStream(path))) {
            String input = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            Gson gson = new Gson();
            return gson.fromJson(input, ProcessConfig[].class);
        } catch (FileNotFoundException e) {
            throw new LedgerException(ErrorMessage.ConfigFileNotFound);
        } catch (IOException | JsonSyntaxException e) {
            throw new LedgerException(ErrorMessage.ConfigFileFormat);
        }
    }

}
