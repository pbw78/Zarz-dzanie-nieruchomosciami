package pl.dealmaker.mobile;

import android.content.Context;
import android.net.Uri;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Writes a small standards-compliant XLSX without a heavyweight Excel library. */
public final class ExcelExporter {
    private ExcelExporter() {}

    public static int write(Context context, Uri uri) throws Exception {
        MobileDb db = new MobileDb(context.getApplicationContext());
        JSONArray rows = db.search("", 5000);
        db.close();
        try (OutputStream raw = context.getContentResolver().openOutputStream(uri, "w");
             ZipOutputStream zip = new ZipOutputStream(raw)) {
            put(zip,"[Content_Types].xml",contentTypes());
            put(zip,"_rels/.rels",rels());
            put(zip,"xl/workbook.xml",workbook());
            put(zip,"xl/_rels/workbook.xml.rels",workbookRels());
            put(zip,"xl/worksheets/sheet1.xml",leadsSheet(rows));
            put(zip,"xl/worksheets/sheet2.xml",summarySheet(rows));
        }
        return rows.length();
    }

    static String leadsSheet(JSONArray a) {
        String[] h={"Typ","Kategoria","Tytuł","Cena","Waluta","Lokalizacja","Źródło","URL","Data","Status","Score","Synchronizacja","Opis"};
        StringBuilder x=new StringBuilder(xmlHead()); x.append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData>");
        rowStrings(x,1,h);
        for(int i=0;i<a.length();i++){
            JSONObject j=a.optJSONObject(i);if(j==null)continue;int r=i+2;x.append("<row r=\"").append(r).append("\">");
            s(x,"A",r,DealClassifier.type(j));
            s(x,"B",r,DealClassifier.category(j));
            s(x,"C",r,j.optString("title",""));
            if(j.has("price")&&!j.isNull("price"))n(x,"D",r,j.optDouble("price"));else s(x,"D",r,"");
            s(x,"E",r,j.optString("currency",""));
            s(x,"F",r,j.optString("location",""));
            s(x,"G",r,j.optString("source_name",""));
            s(x,"H",r,j.optString("source_url",""));
            s(x,"I",r,j.optString("published_at",""));
            s(x,"J",r,j.optString("verification_status",""));
            n(x,"K",r,j.optInt("score",DealClassifier.quickScore(j)));
            s(x,"L",r,j.optInt("sync_state",0)==1?"SYNC":"LOKALNIE");
            s(x,"M",r,j.optString("description",""));
            x.append("</row>");
        }
        x.append("</sheetData><autoFilter ref=\"A1:M").append(Math.max(1,a.length()+1)).append("\"/><sheetViews><sheetView workbookViewId=\"0\"><pane ySplit=\"1\" topLeftCell=\"A2\" activePane=\"bottomLeft\" state=\"frozen\"/></sheetView></sheetViews></worksheet>");
        return x.toString();
    }

    static String summarySheet(JSONArray a) {
        LinkedHashMap<String,Integer> cats=new LinkedHashMap<>(),srcs=new LinkedHashMap<>(),types=new LinkedHashMap<>();
        for(int i=0;i<a.length();i++){JSONObject j=a.optJSONObject(i);if(j==null)continue;inc(cats,DealClassifier.category(j));inc(srcs,j.optString("source_name","Nieznane"));inc(types,DealClassifier.type(j));}
        StringBuilder x=new StringBuilder(xmlHead());x.append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData>");
        rowStrings(x,1,new String[]{"PODSUMOWANIE","Liczba"});
        int r=2;s(x,"A",r,"Wszystkie leady");n(x,"B",r++,a.length());
        r++;rowStringsAt(x,r++,new String[]{"Typ okazji","Liczba"});for(Map.Entry<String,Integer>e:types.entrySet()){s(x,"A",r,e.getKey());n(x,"B",r++,e.getValue());}
        r++;rowStringsAt(x,r++,new String[]{"Kategoria","Liczba"});for(Map.Entry<String,Integer>e:cats.entrySet()){s(x,"A",r,e.getKey());n(x,"B",r++,e.getValue());}
        r++;rowStringsAt(x,r++,new String[]{"Źródło","Liczba"});for(Map.Entry<String,Integer>e:srcs.entrySet()){s(x,"A",r,e.getKey());n(x,"B",r++,e.getValue());}
        x.append("</sheetData></worksheet>");return x.toString();
    }

    private static void inc(Map<String,Integer>m,String k){String key=k==null||k.trim().isEmpty()?"Nieznane":k.trim();m.put(key,m.getOrDefault(key,0)+1);}
    private static void rowStrings(StringBuilder x,int r,String[] vals){rowStringsAt(x,r,vals);}
    private static void rowStringsAt(StringBuilder x,int r,String[] vals){x.append("<row r=\"").append(r).append("\">");for(int i=0;i<vals.length;i++)s(x,col(i+1),r,vals[i]);x.append("</row>");}
    private static void s(StringBuilder x,String col,int row,String v){x.append("<c r=\"").append(col).append(row).append("\" t=\"inlineStr\"><is><t xml:space=\"preserve\">").append(esc(v)).append("</t></is></c>");}
    private static void n(StringBuilder x,String col,int row,double v){if(Double.isNaN(v)||Double.isInfinite(v)){s(x,col,row,"");return;}x.append("<c r=\"").append(col).append(row).append("\"><v>").append(v).append("</v></c>");}
    private static String col(int n){StringBuilder b=new StringBuilder();while(n>0){n--;b.insert(0,(char)('A'+n%26));n/=26;}return b.toString();}
    private static String esc(String s){return (s==null?"":s).replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&apos;").replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]","");}
    private static String xmlHead(){return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>";}
    private static void put(ZipOutputStream z,String name,String text)throws Exception{z.putNextEntry(new ZipEntry(name));z.write(text.getBytes(StandardCharsets.UTF_8));z.closeEntry();}
    private static String contentTypes(){return xmlHead()+"<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/><Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/><Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/><Override PartName=\"/xl/worksheets/sheet2.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/></Types>";}
    private static String rels(){return xmlHead()+"<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/></Relationships>";}
    private static String workbook(){return xmlHead()+"<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><sheets><sheet name=\"Leady\" sheetId=\"1\" r:id=\"rId1\"/><sheet name=\"Podsumowanie\" sheetId=\"2\" r:id=\"rId2\"/></sheets></workbook>";}
    private static String workbookRels(){return xmlHead()+"<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/><Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet2.xml\"/></Relationships>";}
}
