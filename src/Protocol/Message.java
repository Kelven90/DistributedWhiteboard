/**
 * Name: Kelven Lai    Student ID: 1255199
 */

package Protocol;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

public class Message {
    private String type;
    private String data;

    public Message(String type, String data) {
        this.type = type;
        this.data = data;
    }

    public String getType() {
        return type;
    }

    public String getData() {
        return data;
    }

    // Converts this object to a JSON string
    public String toJSON() {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("type", type);
        jsonObject.put("data", data);
        return jsonObject.toJSONString();
    }

    // Converts a JSON string to a Message object
    public static Message fromJSON(String json) throws ParseException {
        JSONParser parser = new JSONParser();
        JSONObject jsonObject = (JSONObject) parser.parse(json);
        String type = (String) jsonObject.get("type");
        String data = (String) jsonObject.get("data");
        return new Message(type, data);
    }
}
