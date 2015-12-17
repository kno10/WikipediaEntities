package com.github.kno10.wikipediaentities;

import java.io.IOException;
import java.io.InputStream;

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
      while(parser.getCurrentToken() != JsonToken.END_ARRAY) {
        assert (parser.getCurrentToken() == JsonToken.START_OBJECT);
        JsonNode tree = parser.readValueAsTree();
        JsonNode idn = tree.path("id");
        if(!idn.isTextual()) {
          System.err.println("Skipping entry without ID. " + parser.getCurrentLocation().toString());
          continue;
        }
        buf.setLength(0);
        buf.append(tree.path("id").asText());
        JsonNode sl = tree.path("sitelinks");
        boolean good = false;
        for(int i = 0; i < wikis.length; i++) {
          JsonNode wln = sl.path(wikis[i]).path("title");
          buf.append('\t');
          if (wln.isTextual()) {
            buf.append(wln.asText());
            good |= true;
          }
        }
        if (good) {
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
