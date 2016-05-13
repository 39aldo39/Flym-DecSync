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

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.Writer;
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
import static org.junit.Assert.assertTrue;


@RunWith(MockitoJUnitRunner.class)
public class ArticleTextExtractorTest {

    @Mock
    Context mMockContext;

    @Before
    public void setUp() {
        MainApplication.setContext(mMockContext);
    }

    @Test
    public void fullContentChip1() throws Exception {
       
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
    public void fullContentChip() throws Exception {
        checkArticle("http://www.chip.de/news/Hoellischer-Gameboy-Doom-II-auf-Raspberry-Pi-Kettensaege-zocken_93519098.html",
                "Höllischer Gameboy: Doom II auf Raspberry-Pi-Kettensäge zocken",
                "Viel stilvoller kann man Doom II kaum spielen: Ein Bastler hat eine blutverschmierte Kettensäge mit ",
                100,
                1,
                new String[]{"Viel stilvoller kann man", "320 x 240 Pixeln in den leeren Körper.", "ab 18 Jahren, versteht sich."});
        checkArticle("http://www.chip.de/news/Volles-Rohr-Der-Hyperloop-faehrt-zum-ersten-Mal_93581140.html",
                "Volles Rohr: Der Hyperloop fährt zum ersten Mal",
                "Der Hyperloop galt lange als Hirngespinst des Milliardärs Elon Musk. Jetzt soll die Vision tatsächli",
                2194,
                6,
                new String[]{"Mit 1.200 km/h durch die Wüste", "1.200 km/h unterwegs sein sollen."});
        checkArticle("http://www.chip.de/news/Raspberry-Pi-3-kaufen-Netzteil-Speicherkarte-Gehaeuse_92705030.html",
                "Raspberry Pi 3 kaufen: Netzteil, Speicherkarte & Co. - so günstig ist der Pi wirklich",
                "Wer den Raspberry Pi 3 kaufen möchte, benötigt für den Betrieb auch Zubehör: etwa ein leistungsstark",
                3747,
                12,
                new String[]{"Raspberry Pi 3 im Check", "Und das ist letztlich der entscheidende Punkt."});
        checkArticle("http://www.chip.de/news/DVB-T2-Empfang-in-Deutschland_91615628.html",
                "DVB-T2 Empfang in Deutschland: Alle kostenlosen HD-Sender",
                "Am 31. Mai kommt die Einführung von DVB-T2 in Deutschland: Ab diesem Datum startet der kostenlose DV",
                3670,
                8,
                new String[]{"Hier startet DVB-T2 in Deutschland", "ab Mitte 2019 gewährleistet wird."});
//        checkArticle("http://www.chip.de/news/Microsoft-kann-das-wahr-sein-Kommt-das-Surface-Book-2-schon-naechsten-Monat_93520134.html",
//                "Microsoft, kann das wahr sein? Surface Book 2 soll schon nächsten Monat kommen",
//                "Das Surface Book ist kaum auf dem Markt, da brodelt schon die Gerüchteküche: Bereits im Juni soll Mi",
//                1000,
//                1,
//                new String[]{});
//        checkArticle("http://www.chip.de/news/China-iPhone-Vorsicht-vor-diesem-Lederklon_93265123.html",
//                "China-iPhone: Vorsicht vor diesem Lederklon",
//                "Das iPhone kennen Sie? Dann passen Sie jetzt mal auf, was in China als \"iPhone\" gehandelt wird. Denn",
//                1000,
//                1,
//                new String[]{});
//        checkArticle("http://www.chip.de/news/Quantencomputer-gratis-Hier-testen-Sie-den-Rechner-der-Zukunft_93265004.html",
//                "Quantencomputer gratis: Hier testen Sie den Rechner der Zukunft",
//                "Quantencomputer gelten schon lange als die Zukunft schlechthin - jetzt ist der erste Computer für je",
//                1000,
//                1,
//                new String[]{});
//        checkArticle("http://www.chip.de/news/Fette-1-TByte-SSD-guenstig-wie-nie-Toshiba-Q300-jetzt-abstauben_93176348.html",
//                "Fette 1-TByte-SSD günstig wie nie: Toshiba Q300 jetzt abstauben",
//                "Große SSDs werden immer erschwinglicher. Momentan erhalten Sie zum Beispiel die schnelle Toshiba Q30",
//                1000,
//                1,
//                new String[]{});
//        checkArticle("http://www.chip.de/news/Nvidias-neue-Grafikkarten-GeForce-GTX-1070-und-1080-Release-und-Preis_93379054.html",
//                "Nvidia GTX 1070 & 1080 angekündigt: Sie kommen bald und der Preis steht fest!",
//                "Nvidia hat seine neuen Grafikkarten Geforce GTX 1070 und GTX 1080 vorgestellt. Hier ist alles, was S",
//                1000,
//                1,
//                new String[]{});
//        checkArticle("http://www.chip.de/news/Diese-Ente-verzueckt-YouTube-Laeuft-dank-3D-Drucker_93366930.html",
//                "Prothese statt Pastete: YouTube-Ente von 3D-Drucker gerettet",
//                "Wie süß! Die Ente \"Phillip\" kann dank 3D-Drucker wieder laufen. Sehen Sie hier das niedliche YouTube",
//                1000,
//                1,
//                new String[]{});
//        checkArticle("http://www.chip.de/news/Besser-als-jeder-Fernseher-Dieser-Beamer-ist-der-Hammer_93264321.html",
//                "Besser als jeder Fernseher: Dieser Beamer ist der Hammer!",
//                "Mit dem Screeneo 2.0 HDP2510 bringt Philips einen Shortthrow-Beamer wie man ihn sich wünscht. Das Ge",
//                1000,
//                1,
//                new String[]{});
    }
    
    @Test
    public void parseRss() throws Exception {
        

        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
        String rssUrl = 
//                "http://tusfleestedt.de/index.php?format=feed&type=rss";
                "http://www.chip.de/rss/rss_technik.xml"
                ;
        URL url = new URL(rssUrl);
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
            System.out.println("checkArticle(\""+link+"\",\n" + "\""+title+"\",\n" +"\""+ d.text().substring(0, 100)+"\",\n100,\n1,\nnew String[]{});");
        }
        
        
    }
    
    private void checkArticle(String articleUrl, String titleIndicator, String contentIndicator, int maxLength, int numberOfImgs, String[] excerpts) throws Exception {

        URL url = new URL(articleUrl);
        URLConnection con = url.openConnection();
        InputStream in = con.getInputStream();

        String s = ArticleTextExtractor.extractContent(in, contentIndicator, titleIndicator);
        Document doc = Jsoup.parse(s);

        System.out.println(articleUrl);
        String content = doc.text().replaceAll("(.{200})", "$1\n");
//        System.out.println(content);
        System.out.println(doc.text().length());
        System.out.println(doc.select("img").size());
        for (int i = 0; i < doc.select("img").size(); i++) {
            Element e = doc.select("img").get(i);
            String src = e.attr("src");
            if (e.parent() != null) {
                if(e.parent().hasAttr("data-lazy-image")) {
                    src = e.parent().attr("data-lazy-image");
                }
            }
            System.out.println(src +": "+e.attr("alt"));
        }
        if (doc.text().length() < maxLength) {
            System.out.println("############################ GOOD! ###########################################");
        }
        System.out.println("-------------------------------------------------------------------------------");

        File temp = File.createTempFile("fullArticle", ".html");
        BufferedWriter output = new BufferedWriter(new FileWriter(temp));
        String mobilizedHtml = HtmlUtils.improveHtmlContent(s, NetworkUtils.getBaseUrl(articleUrl));
        output.write(mobilizedHtml);
        output.close();
        Runtime rTime = Runtime.getRuntime();
//        String url2 = "file://c:/temp/hi.html";
        String browser = "C:/Program Files/Internet Explorer/iexplore.exe ";
        Process pc = rTime.exec(browser + temp.toURI().toURL());
        pc.waitFor();
        
        assertTrue(false);
               
        

        assertEquals("Wrong numbers of images within full content.", doc.select("img").size(), numberOfImgs);
        for (String e : excerpts) {
            assertThat(doc.text(), containsString(e));    
        }
        assertThat("Extracted document is probably too long!", doc.text().length(), is(lessThanOrEqualTo(maxLength)));
        
       
    }

    
}