package ms.hispam.budget.config;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import ms.hispam.budget.entity.mysql.NominaPaymentComponentLink;

import java.io.IOException;
import java.util.*;

public class NominaPaymentLinksDeserializer extends JsonDeserializer<Map<Integer, List<NominaPaymentComponentLink>>> {

    @Override
    public Map<Integer, List<NominaPaymentComponentLink>> deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        JsonNode node = p.getCodec().readTree(p);
        Map<Integer, List<NominaPaymentComponentLink>> result = new HashMap<>();

        if (node.isArray()) {
            // Si es un array, asumimos que todos los elementos pertenecen al año actual
            int currentYear = Calendar.getInstance().get(Calendar.YEAR);
            List<NominaPaymentComponentLink> links = new ArrayList<>();
            for (JsonNode elementNode : node) {
                NominaPaymentComponentLink link = p.getCodec().treeToValue(elementNode, NominaPaymentComponentLink.class);
                links.add(link);
            }
            result.put(currentYear, links);
        } else if (node.isObject()) {
            // Si es un objeto, lo tratamos como un mapa
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                int year = Integer.parseInt(entry.getKey());
                List<NominaPaymentComponentLink> links = new ArrayList<>();
                for (JsonNode elementNode : entry.getValue()) {
                    NominaPaymentComponentLink link = p.getCodec().treeToValue(elementNode, NominaPaymentComponentLink.class);
                    links.add(link);
                }
                result.put(year, links);
            }
        }

        return result;
    }
}