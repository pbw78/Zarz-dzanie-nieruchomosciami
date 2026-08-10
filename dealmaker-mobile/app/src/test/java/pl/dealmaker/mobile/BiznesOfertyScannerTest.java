package pl.dealmaker.mobile;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.junit.Test;
import static org.junit.Assert.*;

public class BiznesOfertyScannerTest {
    @Test public void findsOfferLinksAndImageAltTitle() {
        String html="<html><body>"+
                "<a href='/o/testowa-oferta%2C259201.html'><img alt='Testowa firma na sprzedaż'></a>"+
                "<a href='/o/testowa-oferta%2C259201.html'>Testowa firma na sprzedaż</a>"+
                "<a href='/kontakt/'>Kontakt</a></body></html>";
        Document d=Jsoup.parse(html,"https://www.biznesoferty.pl/");
        assertEquals(2,BiznesOfertyScanner.offerLinks(d).size());
        Element first=BiznesOfertyScanner.offerLinks(d).first();
        assertNotNull(first);
        assertEquals("Testowa firma na sprzedaż",BiznesOfertyScanner.titleFor(first));
    }

    @Test public void buildsRealPaginationShape() {
        assertEquals("https://www.biznesoferty.pl/sprzedam-biznes/gastronomia/",BiznesOfertyScanner.categoryUrl("gastronomia",1));
        assertEquals("https://www.biznesoferty.pl/sprzedam-biznes/gastronomia,2/",BiznesOfertyScanner.categoryUrl("gastronomia",2));
    }
}
