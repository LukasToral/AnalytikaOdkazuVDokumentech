package com.company;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Main {


    static Path cesta = Paths.get("/Users/lukasmac/Downloads/AnalytikaOdkazu/odkazyCSV.csv");

    /**
     * Funkce pro převod hex textu na String
     *
     * @param hexText
     * @return cb.toString()
     */
    private static String hexToString(String hexText) {
        ByteBuffer buff = ByteBuffer.allocate(hexText.length() / 2);
        for (int i = 0; i < hexText.length(); i += 2) {
            buff.put((byte) Integer.parseInt(hexText.substring(i, i + 2), 16));
        }
        buff.rewind();
        Charset cs = Charset.forName("UTF-8");
        CharBuffer cb = cs.decode(buff);
        return cb.toString();
    }

    /**
     * Funkce, která projde celým stringem a hledá v něm části ohrničené složenými závorkami
     *
     * @param text
     * @return linkList
     */
    private static List<String> getLinks(String text) {
        Pattern p = Pattern.compile("\\{.*?\\}");
        Matcher m = p.matcher(text);
        List<String> linkList = new ArrayList<String>();
        int i = 0;
        while (m.find()) {
            String zapis = (String) m.group().subSequence(1, m.group().length() - 1);
            linkList.add(i, zapis);
            i++;
        }
        return linkList;
    }

    private static boolean hasMoreThanOneLine(Path pwd) throws IOException {
        boolean headerCheck = false;
        BufferedReader reader = new BufferedReader(new FileReader(String.valueOf(pwd)));
        int lines = 0;
        while (reader.readLine() != null) {
            lines++;
            if (lines > 1 ) {
                headerCheck = true;
                break;
            }
        }
        reader.close();
        return headerCheck;
    }

    /**
     * Funkce pro zapsání požadovaných odkazů do souboru
     *
     * @param polozkyProZapsani
     * @param docmanId
     * @throws IOException
     */
    private static void fileWriter(List<String> polozkyProZapsani, String docmanId) throws IOException {
        String[] HEADERS = {"TextOdkazu", "Zdroj", "Cil", "MistoVCili"};
        FileWriter out = new FileWriter("odkazyCSV.csv", true);
        try (CSVPrinter printer = new CSVPrinter(out, CSVFormat.DEFAULT.withHeader(HEADERS).withSkipHeaderRecord(hasMoreThanOneLine(cesta))
        )) {
            for (String item : polozkyProZapsani) {
                if (item.charAt(0) == 'j') {
                    item = item.substring(1);
                    String[] casti = item.split("\\\\");
                    for (String castOdkazu : casti) {
                        printer.print(castOdkazu);
                        System.out.println(docmanId);
                    }
                    printer.print(docmanId);
                    printer.println();
                }
            }
        }
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        if (Files.exists(cesta)) {
            Files.delete(cesta);
            System.out.println("Soubor smazán");
        }
        //Vytvoření HttpClienta
        HttpClient httpClient = HttpClient.newBuilder()
                .build();

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("http://192.168.2.238:8889/rest/repo/getIds/e3d14a3457eb4bc6b2c0e72cac4c4963199521e354a35184987ffc98f3cd1959/cz.atlascon.etic.PTF/"))
                .header("Content-Type", "application/json")
                .GET()
                .build();

        HttpResponse<String> resReq = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        JSONArray jSONArray = new JSONArray(resReq.body());
        int length = jSONArray.length();
        for (int i = 0; i < length; i++) {
            JSONObject jSONObject = jSONArray.getJSONObject(i);
            /**
             * Vytvoření HttpRequestu na GraphQL endpoint - http://192.168.2.238:8889/rest/graphql/e3d14a3457eb4bc6b2c0e72cac4c4963199521e354a35184987ffc98f3cd1959
             * Požadovaný media type je application/json
             * K endpointu přidávám query pro vrácení požadovaných dat - {  cz_atlascon_etic_PTF(docmanId: 123, dz: CR) { docmanId, dz, data }}
             */
            HttpRequest mainRequest = HttpRequest.newBuilder()
                    .uri(URI.create("http://192.168.2.238:8889/rest/graphql/e3d14a3457eb4bc6b2c0e72cac4c4963199521e354a35184987ffc98f3cd1959"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString("{ \"query\": \"{  cz_atlascon_etic_PTF(docmanId: " + jSONObject.get("docmanId").toString() + ", dz: " + jSONObject.get("dz").toString() + ") { docmanId, dz, data }}\" }"))
                    .build();
            HttpResponse<String> response = httpClient.send(mainRequest, HttpResponse.BodyHandlers.ofString());

            //Úprava získaného Stringu na Json
            JsonObject obj = new Gson().fromJson(response.body(), JsonObject.class);
            JsonObject res = obj.get("data").getAsJsonObject();
            JsonArray pole = res.get("cz_atlascon_etic_PTF").getAsJsonArray();
            JsonObject cz_atlascon_etic_PTF = pole.get(0).getAsJsonObject();
            fileWriter(getLinks(hexToString(cz_atlascon_etic_PTF.get("data").getAsString())), cz_atlascon_etic_PTF.get("docmanId").getAsString());
        }

    }
}



