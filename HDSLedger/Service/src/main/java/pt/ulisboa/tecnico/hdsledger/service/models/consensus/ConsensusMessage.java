package pt.ulisboa.tecnico.hdsledger.service.models.consensus;

import com.google.gson.Gson;

import java.io.Serializable;

public abstract class ConsensusMessage implements Serializable {

    @Override
    public String toString() {
        return new Gson().toJson(this);
    }

    public String toJson() {
        return this.toString();
    }

}
