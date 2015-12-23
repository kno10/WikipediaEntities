package com.github.kno10.wikipediaentities;

import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.kno10.wikipediaentities.util.Util;

/**
 * Load a WikiData dump, to match Wikipedia articles across languages.
 *
 * @author Erich Schubert
 */
public class LoadWikiData {
  public void load(String fname, String... wikis) throws IOException {
    JsonFactory jackf = new JsonFactory();
    jackf.disable(JsonParser.Feature.AUTO_CLOSE_SOURCE);
    try (InputStream in = Util.openInput(fname);
        JsonParser parser = jackf.createParser(in)) {
      parser.setCodec(new ObjectMapper());
      parser.nextToken();
      assert (parser.getCurrentToken() == JsonToken.START_ARRAY);
      parser.nextToken();

      StringBuilder buf = new StringBuilder();
      buf.append("WikiDataID");
      for(int i = 0; i < wikis.length; i++) {
        buf.append('\t').append(wikis[i]);
      }
      buf.append('\n');
      System.out.print(buf.toString());

      lines: while(parser.getCurrentToken() != JsonToken.END_ARRAY) {
        assert (parser.getCurrentToken() == JsonToken.START_OBJECT);
        JsonNode tree = parser.readValueAsTree();
        JsonNode idn = tree.path("id");
        if(!idn.isTextual()) {
          System.err.println("Skipping entry without ID. " + parser.getCurrentLocation().toString());
          continue;
        }
        // Check for instance-of for list and category pages:
        JsonNode claims = tree.path("claims");
        JsonNode iof = claims.path("P31");
        if(iof.isArray()) {
          for(Iterator<JsonNode> it = iof.elements(); it.hasNext();) {
            final JsonNode child = it.next();
            JsonNode ref = child.path("mainsnak").path("datavalue").path("value").path("numeric-id");
            if(ref.isInt()) {
              if(ref.asInt() == 13406463) { // "Wikimedia list article"
                continue lines;
              }
              if(ref.asInt() == 4167836) { // "Wikimedia category article"
                continue lines;
              }
              if(ref.asInt() == 4167410) { // "Wikimedia disambiguation page"
                continue lines;
              }
              // Not reliable: if(ref.asInt() == 14204246) { // "Wikimedia
              // project page"
            }
          }
        }
        buf.setLength(0);
        buf.append(idn.asText());
        JsonNode sl = tree.path("sitelinks");
        boolean good = false;
        for(int i = 0; i < wikis.length; i++) {
          JsonNode wln = sl.path(wikis[i]).path("title");
          buf.append('\t');
          if(wln.isTextual()) {
            buf.append(wln.asText());
            good |= true;
          }
        }
        if(good) {
          buf.append('\n');
          System.out.print(buf.toString());
        }
        parser.nextToken();
      }
    }
  }

  public static void main(String[] args) {
    try {
      new LoadWikiData().load("wikidata-20151214-all.json.bz2", "enwiki", "dewiki", "eswiki", "frwiki");
    }
    catch(IOException e) {
      e.printStackTrace();
    }
  }
}
