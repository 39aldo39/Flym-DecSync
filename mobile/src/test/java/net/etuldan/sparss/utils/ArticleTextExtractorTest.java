package net.etuldan.sparss.utils;

import android.content.Context;
import android.test.AndroidTestCase;
import android.text.Html;

import net.etuldan.sparss.MainApplication;
import net.etuldan.sparss.parser.RssAtomParser;

import org.hamcrest.CoreMatchers;
import org.hamcrest.core.SubstringMatcher;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.Date;


import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertEquals;


@RunWith(MockitoJUnitRunner.class)
public class ArticleTextExtractorTest {

    @Mock
    Context mMockContext;


    @Before
    public void setUp() {
        System.out.println("setUp");
        MainApplication.setContext(mMockContext);
    }

    @Test
    public void addition_isCorrect() throws Exception {
        assertEquals(4, 2 + 2);
    }

    @Test
    public void emailValidator_CorrectEmailSimple_ReturnsTrue() {
        assertThat(true, is(true));
    }

    @Test
    public void fullContentChip() throws Exception {
        String RssChip = "http://www.chip.de/rss/rss_technik.xml";
        String artChip = "http://www.chip.de/news/Besser-als-jeder-Fernseher-Dieser-Beamer-ist-der-Hammer_93264321.html";
        
        URL url = new URL(artChip);
        URLConnection con = url.openConnection();
        InputStream in = con.getInputStream();

        String titleIndicator = "Besser als jeder Fernseher: Dieser Beamer ist der Hammer!";
        String contentIndicator = "Mit dem Screeneo 2.0 HDP2510 bringt Philips einen Shortthrow-Beamer wie man ihn sich wünscht. Das Ge";
        String s = ArticleTextExtractor.extractContent(in, contentIndicator, titleIndicator);

        Assert.assertThat(s, CoreMatchers.allOf(containsString("Mit dem Screeneo 2.0 HDP2510"), containsString("Goodbye, Fernseher!"),containsString("Euro im Handel")));
        assertThat("Extracted document is probably too long!", s.length(), is(lessThanOrEqualTo(23000)));
    }

    @Test
    public void fullContentTus() throws Exception {
        checkArticle("http://tusfleestedt.de/index.php/alle-sportberichte/fussball/396-e-juniorinnen-starten-beim-vgh-cup-in-neu-wulmstorf",
                "E-Juniorinnen starteten beim VGH-Cup in Neu Wulmstorf",
                "Am Sonnabend traten unsere E-Juniorinnen beim landesweiten Sichtungsturnier des NFV in Neu Wulmstorf",
                872,
                1,
                new String[]{"Am Sonnabend traten ", "Erstmals spielten ", "wieder die Torstatistik!"});
        checkArticle("http://tusfleestedt.de/index.php/alle-sportberichte/tischtennis/395-tischtennis-1-damenmannschaft-gewinnt-kreispokal",
                "Tischtennis: 1. Damenmannschaft gewinnt Kreispokal!",
                "Unsere 1. Damenmannschaft hat mit dem Gewinn des Kreispokals eine äußerst erfolgreiche Saison abgesc",
                509,
                1,
                new String[]{"Unsere 1. Damenmannschaft", "Die Meisterschaft in", "gehalten werden kann." });
        checkArticle("http://tusfleestedt.de/index.php/homepage/foerderverein/394-foerderverein-bietet-foerdermitgliedschaften-an",
                "Förderverein bietet Fördermitgliedschaften an",
                "Ein modernes und anspruchsvolles Sportangebot bei moderaten Mitgliedsbeiträgen zu bieten – dies ist ",
                1332,
                1,
                new String[]{});
        checkArticle("http://tusfleestedt.de/index.php/sportangebot/kursangebot/fitness/393-smovey",
                "Smovey",
                "Smoveys bringen Abwechslung in die Sportstunden! Ab 12.05.2016  (6 Termine) Zeit: Donnerstags 10.15 ",
                1060,
                1,
                new String[]{"Smoveys bringen Abwechslung", "12.05., 19.05., 02.06,", "Tel. 04105-4235."});
        checkArticle("http://tusfleestedt.de/index.php/sportangebot/kursangebot/tanzen-und-rhythmische-bewegung/390-taenzer-innen-ue60-gesucht",
                "Tänzer/innen \"Ü60\" gesucht!",
                "Die Chance für Alle, die Lust zu tanzen haben aber partner/innenlos sind! Anfängerkurs „Ü60 Tanzen u",
                842,
                2,
                new String[]{"Die Chance für Alle", "12. Mai bis 16. Juni (5 Termine)", "Tel. 04105/1534081"});
        checkArticle("http://tusfleestedt.de/index.php/alle-sportberichte/fussball/389-fussballerinnen-des-tus-fleestedt-wollen-im-neuen-sportzentrum-durchstarten",
                "Fußballerinnen des TuS Fleestedt wollen im neuen Sportzentrum durchstarten",
                "Seevetal-Fleestedt – Bereits seit über 20 Jahren spielen auch Frauen und Mädchen beim TuS Fleestedt ",
                1812,
                1,
                new String[]{"Seevetal-Fleestedt – Bereits", "Rund 50 Mädchen spielen","Fotos: Ulrich Vergin"});
        checkArticle("http://tusfleestedt.de/index.php/50-neues-aus-dem-tus/377-muellsammelaktion-jedermaenner-waren-dabei",
                "Müllsammelaktion - JederMänner waren dabei",
                "Lost in Fleestedt Tatort: Müllsammelaktion der Landesjägerschaft am 09.04.2016. Und wir, eine 8-",
                1125,
                1,
                new String[]{"Voller Tatendrang waren wir so", "Karl-Heinz Jahn"});
        checkArticle("http://tusfleestedt.de/index.php/alle-sportberichte/fussball/376-platzeinleuchtung",
                "Platzeinleuchtung",
                "Einleuchtung der beiden Fußballplätze Am 11. April ab 21:00 Uhr wurde der erste Testlauf der Fußball",
                878,
                1,
                new String[]{"Einleuchtung der beiden Fußballplätze", "den Fußballern begrüßt."});
        checkArticle("http://tusfleestedt.de/index.php/50-neues-aus-dem-tus/374-wellness-massage",
                "Wellness-Massage ",
                "Wellness-Massage im Sportzentrum Seevetal Ab Juni 2016 Nach dem Training kommt die Wellness-Massage ",
                947,
                2,
                new String[]{"Wellness-Massage im Sportzentrum Seevetal Ab Juni 2016", "0174-3770087."});
        checkArticle("http://tusfleestedt.de/index.php/sportangebot/spartenangebote/fitness/yoga-und-faszientraining",
                "Yoga und Faszientraining",
                "Yoga & Faszien Zeit: dienstags von 19.45 - 21.15 Uhr Ort: Sportzentrum Seevetal Übungsleiterin / Ans",
                723,
                0,
                new String[]{"Yoga & Faszien","klebungen gelöst.","Sylwia Kistenmacher"});
    }

    @Test
    public void fullContentTusRss() throws Exception {
        

        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
        URL url = new URL("http://tusfleestedt.de/index.php?format=feed&type=rss");
        URLConnection con = url.openConnection();
        InputStream in = con.getInputStream();
        org.w3c.dom.Document doc = dBuilder.parse(in);
        doc.getDocumentElement().normalize();
        org.w3c.dom.Element e = doc.getDocumentElement();
        NodeList entries = e.getElementsByTagName("item");
        for(int i = 0; i < entries.getLength(); i++) {
            org.w3c.dom.Element entry = (org.w3c.dom.Element)entries.item(i);
            entry.normalize();
            String title = entry.getElementsByTagName("title").item(0).getTextContent();
            String link = entry.getElementsByTagName("link").item(0).getTextContent();
            String description = entry.getElementsByTagName("description").item(0).getTextContent();

            Document d = Jsoup.parse(description);
            System.out.println("checkArticle(\""+link+"\",\n" + "\""+title+"\",\n" +"\""+ d.text().substring(0, 100)+"\",\n1000,\n1,\nnew String[]{});");
        }
        
        
    }
    
    private void checkArticle(String articleUrl, String titleIndicator, String contentIndicator, int maxLength, int numberOfImgs, String[] excerpts) throws Exception {

        String article = "http://tusfleestedt.de/index.php/alle-sportberichte/tischtennis/395-tischtennis-1-damenmannschaft-gewinnt-kreispokal";

        URL url = new URL(articleUrl);
        URLConnection con = url.openConnection();
        InputStream in = con.getInputStream();

        String s = ArticleTextExtractor.extractContent(in, contentIndicator, titleIndicator);
        Document doc = Jsoup.parse(s);

//        System.out.println(s);
        System.out.println(doc.text().length());
//        System.out.println(doc.select("img").size());

        assertEquals(doc.select("img").size(), numberOfImgs);
        for (String e : excerpts) {
            assertThat(doc.text(), containsString(e));    
        }
        assertThat("Extracted document is probably too long!", doc.text().length(), is(lessThanOrEqualTo(maxLength)));
        
       
    }

    
}