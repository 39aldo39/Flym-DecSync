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
import org.xml.sax.InputSource;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PushbackInputStream;
import java.io.Reader;
import java.io.Writer;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.Date;
import java.util.zip.GZIPInputStream;


import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import static net.etuldan.sparss.utils.HtmlUtils.decompressStream;
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
                964,
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
                2111,
                2,
                new String[]{"Viel stilvoller kann man", "320 x 240 Pixeln in den leeren Körper.", "ab 18 Jahren, versteht sich."});
        checkArticle("http://www.chip.de/news/Volles-Rohr-Der-Hyperloop-faehrt-zum-ersten-Mal_93581140.html",
                "Volles Rohr: Der Hyperloop fährt zum ersten Mal",
                "Der Hyperloop galt lange als Hirngespinst des Milliardärs Elon Musk. Jetzt soll die Vision tatsächli",
                1801,
                1,
                new String[]{"Mit 1.200 km/h durch die Wüste", "1.200 km/h unterwegs sein sollen."});
        checkArticle("http://www.chip.de/news/Raspberry-Pi-3-kaufen-Netzteil-Speicherkarte-Gehaeuse_92705030.html",
                "Raspberry Pi 3 kaufen: Netzteil, Speicherkarte & Co. - so günstig ist der Pi wirklich",
                "Wer den Raspberry Pi 3 kaufen möchte, benötigt für den Betrieb auch Zubehör: etwa ein leistungsstark",
                3354,
                7,
                new String[]{"Raspberry Pi 3 im Check", "Und das ist letztlich der entscheidende Punkt."});
        checkArticle("http://www.chip.de/news/DVB-T2-Empfang-in-Deutschland_91615628.html",
                "DVB-T2 Empfang in Deutschland: Alle kostenlosen HD-Sender",
                "Am 31. Mai kommt die Einführung von DVB-T2 in Deutschland: Ab diesem Datum startet der kostenlose DV",
                3277,
                3,
                new String[]{"Hier startet DVB-T2 in Deutschland", "ab Mitte 2019 gewährleistet wird."});
        checkArticle("http://www.chip.de/news/Microsoft-kann-das-wahr-sein-Kommt-das-Surface-Book-2-schon-naechsten-Monat_93520134.html",
                "Microsoft, kann das wahr sein? Surface Book 2 soll schon nächsten Monat kommen",
                "Das Surface Book ist kaum auf dem Markt, da brodelt schon die Gerüchteküche: Bereits im Juni soll Mi",
                1645,
                4,
                new String[]{"Das Surface Book ist kaum auf dem Markt", "damit, dass sich die Akkulaufzeit ", "unserer Kaufberatung einige Ideen."});
        checkArticle("http://www.chip.de/news/China-iPhone-Vorsicht-vor-diesem-Lederklon_93265123.html",
                "China-iPhone: Vorsicht vor diesem Lederklon",
                "Das iPhone kennen Sie? Dann passen Sie jetzt mal auf, was in China als \"iPhone\" gehandelt wird. Denn",
                2398,
                1,
                new String[]{"Das iPhone kennen Sie? Dann", "plausibel darlegen, dass", "alles zum Wohle von iPhone und IPhone."});
        checkArticle("http://www.chip.de/news/Quantencomputer-gratis-Hier-testen-Sie-den-Rechner-der-Zukunft_93265004.html",
                "Quantencomputer gratis: Hier testen Sie den Rechner der Zukunft",
                "Quantencomputer gelten schon lange als die Zukunft schlechthin - jetzt ist der erste Computer für je",
                2068,
                1,
                new String[]{"von Molekülen verwendet werden, etwa für Medikamente."});
        checkArticle("http://www.chip.de/news/Fette-1-TByte-SSD-guenstig-wie-nie-Toshiba-Q300-jetzt-abstauben_93176348.html",
                "Fette 1-TByte-SSD günstig wie nie: Toshiba Q300 jetzt abstauben",
                "Große SSDs werden immer erschwinglicher. Momentan erhalten Sie zum Beispiel die schnelle Toshiba Q30",
                2133,
                8,
                new String[]{"Große SSDs werden immer e", "exzellentes Preis-Leistungs-Verhältnis.", "Alle SSDs im Test"});
        checkArticle("http://www.chip.de/news/Nvidias-neue-Grafikkarten-GeForce-GTX-1070-und-1080-Release-und-Preis_93379054.html",
                "Nvidia GTX 1070 & 1080 angekündigt: Sie kommen bald und der Preis steht fest!",
                "Nvidia hat seine neuen Grafikkarten Geforce GTX 1070 und GTX 1080 vorgestellt. Hier ist alles, was S",
                4952,
                11,
                new String[]{"Nvidia hat seine neuen Grafikkarten Geforce GTX 1070", "Vorgängergeneration deutlich fallen werden.","über 90 mit derselben GTX 1080."});
    }

    @Test
    public void fullContentOora() throws Exception {
        checkArticle("http://www.oora.de/detailansicht/lieber-gott-boeser-gott/",
                "Lieber Gott, böser Gott",
                "Der Gott des Alten Testaments, der das kanaanäische Volk vernichtet, ist uns unheimlich. Wir spreche",
                12439,
                3,
                new String[]{"und Moralaposteln überlassen."});
        checkArticle("http://www.oora.de/detailansicht/meer-mit-himmel/",
                "Meer mit Himmel",
                "Wie kann man in enger Vertrautheit mit Gott leben? Und wieso lohnt es sich, diese Beziehung zu pfleg",
                7577,
                1,
                new String[]{"lebendigen Gott Gemeinschaft haben können."});
    }
    @Test
    public void fullContentStatuscode() throws Exception {
        checkArticle("https://statuscode.ch/2016/02/distribution-packages-considered-insecure",
                "Distribution packages considered insecure",
                "If you ever have run a Linux-based operating system you are probably aware of the way that software ",
                7911,
                2,
                new String[]{"If you ever have run a Linux-based operating system ", "Missing at least one security patch from 4.4.6", "Containers will probably not save you either."});
        checkArticle("https://statuscode.ch/2016/01/subtle-vulnerabilties-with-php-and-curl",
                "Subtle vulnerabilities with PHP and cURL",
                "This post tries to prove that vulnerabilities can in fact be very subtle and that even people who ma",
                2946,
                0,
                new String[]{"This post tries to prove that vulnerabilities ", "to check your own code :-)"});
        checkArticle("https://statuscode.ch/2015/09/ownCloud-security-development-over-the-years",
                "ownCloud security development over the years",
                "A deep look at the numbers It has been over three years now since ownCloud decided in 2012 to issue ",
                7415,
                7,
                new String[]{"A deep look at the numbers", "ownCloud instance. (usually /var/www/owncloud)"});
        checkArticle("https://statuscode.ch/2015/06/Combining-ownCloud-and-Google-calendar-for-public-room-availability",
                "Combining ownCloud and Google calendar for public room availability",
                "In my coworking space we are using ownCloud calendar to keep track of the availability of our confer",
                2574,
                2,
                new String[]{"In my coworking space we are using " ,"public and embedded in web pages:"});
        checkArticle("https://statuscode.ch/2015/02/contributing-back-to-open-source",
                "Contributing back to open-source",
                "In the open-source community the so-called “Linus’s Law” by Eric Raymond is often cited as one of th",
                6263,
                0,
                new String[]{"In the open-source community the so-called ", "(and was also input for some of above points)"});
    }
    
    @Test
    public void fullContentSportmediaset() throws Exception {
        checkArticle("http://www.sportmediaset.mediaset.it/calcio/calcio/serie-a-le-formazioni-in-tempo-reale_1097794-201602a.shtml",
                "38.A GIORNATA|FORMAZIONI LIVE Abate è out\nSi ferma Perotti: Roma con Dzeko\nInter-Palacio, Juve con Hernanes",
                "Fascite plantare per il terzino del Milan, in dubbio anche la finale di Coppa Italia. Affaticamento ",
                8144,
                0,
                new String[]{"Empoli (4-3-1-2): Pugliesi", "Udinese (3-5-2): Karnezis", "Indisponibili: Icardi, Medel, Miranda"});
        checkArticle("http://www.sportmediaset.mediaset.it/mercato/mercato/sampaoli-molto-vicino-al-valencia-ciao-lazio_1098174-201602a.shtml",
                "LA SVOLTA|Sampaoli, ciao Lazio \nValencia a un passo\nSpagnoli su Vidal\n",
                "L'ex ct del Cile aveva trovato un'intesa coi biancocelesti ma Lotito non era del tutto convinto dell",
                1497,
                1,
                new String[]{"L'ex ct del Cile aveva", "Antonio Conte per il suo Chelsea."});
        checkArticle("http://www.sportmediaset.mediaset.it/altrenotizie/mondo/olimpiadi-di-sochi-il-new-york-times-mosca-ha-barato-decine-di-atleti-russi-sono-stati-dopati-_1098035-201602a.shtml",
                "ESTERI|Olimpiadi Sochi, New York Times: \"Atleti russi dopati\"",
                "Lo ha rivelato il direttore del laboratorio anti-doping. Il ministro dello Sport Vitaly Mutko replic",
                2008,
                1,
                new String[]{"Lo ha rivelato il direttore ", "state riportate dall'agenzia Tass."});
        checkArticle("http://www.sportmediaset.mediaset.it/speciale/riminiwellness2016/riminiwellness-l-energy-revolution-torna-sulla-riviera-romagnola_1094794-201602a.shtml",
                "DAL 2 AL 5 GIUGNO|Torna RiminiWellness\nTutte le novità fitness\nper vivere sano e bene",
                "Undicesima edizione della kermesse: padiglioni interattivi, l'insegnamento al benessere e ospiti pre",
                1879,
                1,
                new String[]{"Dal 2 al 5 giugno tutto il meglio ", "si scateneranno in straordinari show cooking."});
    }

    @Test
    public void fullContentNrdc() throws Exception {
        checkArticle("https://www.nrdc.org/experts/jake-schmidt/nordic-countries-and-us-announce-continued-climate-actions",
                "Nordic Countries and U.S. Announce Continued Climate Actions",
                "",
                5392,
                1,
                new String[]{"co-written with Sarah Lyn Vollmer.", "Jake Schmidt Director, International program"});
        checkArticle("https://www.nrdc.org/experts/amy-mall/regulators-neglected-stop-oil-and-gas-wastewater-contamination-west-virginia-creek",
                "Regulators Neglected to Stop Oil and Gas Wastewater Contamination in West Virginia Creek",
                "",
                4691,
                1,
                new String[]{"The United States Geological Survey recently", "Mall Senior Policy Analyst, Land & Wildlife program"});
        checkArticle("https://www.nrdc.org/experts/barbara-finamore/tide-turning-private-sector-joins-chinese-government-and-international",
                "The Tide Is Turning: The Private Sector Joins the Chinese Government and the International Community in Cleaning Up Global Shipping Emissions ",
                "",
                4044,
                3,
                new String[]{"colleagues Freda Fung, Zhixi Zhu, and Winslow Robertson.", "Finamore Senior Attorney and Asia Director, China program"});
        checkArticle("https://www.nrdc.org/stories/saving-breeding-grounds-pacific-gray-whale",
                "Saving the Breeding Grounds of the Pacific Gray Whale",
                "",
                3612,
                1,
                new String[]{"With its warm, shallow waters bounded", "And so, too, do the Pacific gray whales."});
    }

    @Test
    public void fullContentElPais() throws Exception {
        checkArticle("http://elpais.com/ccaa/2016/05/15/catalunya/1463309032_025957.html#?ref=rss&format=simple&link=link",
                "Sánchez presenta en Barcelona el equipo con el que aspira a gobernar",
                "El líder socialista incorpora a su ejecutivo al senegalés Luc André Diouf y a la alcaldesa de Santa ",
                4855,
                0,
                new String[]{"Tras una semana en la que el", "Sevilla, Josep Borrell o Ángel Gabilondo."});
        checkArticle("http://elpais.com/internacional/2016/05/15/colombia/1463323839_160026.html#?ref=rss&format=simple&link=link",
                "Paramilitarismo: la guerra que nunca termina en Colombia",
                "El repunte de la violencia de los herederos de los paramilitares marca la recta final del proceso de",
                5708,
                1,
                new String[]{"El pasado 10 de abril,", "paramilitares que ha tenido Colombia."});
        checkArticle("http://elpais.com/internacional/2016/05/15/estados_unidos/1463327849_813449.html#?ref=rss&format=simple&link=link",
                "Mujeres, impuestos, suplantaciones: el pasado que incomoda a Trump",
                "Nuevas revelaciones de su pasado han puesto a la defensiva al candidato presidencial republicano",
                6086,
                0,
                new String[]{" a mujeres y oscuros casos ", "del republicano se ha distanciado del"});
        checkArticle("http://elpais.com/elpais/2016/05/15/portada/1463314135_231810.html#?ref=rss&format=simple&link=link",
                "En el armario del fútbol español",
                "",
                22759,
                2,
                new String[]{"Es una imagen conocida por cualquiera", "esta semana al estadio."});
    }
    
    /**
     * Helper method for debugging only.
     */
    @Test
    public void parseRss() throws Exception {
        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
        String rssUrl = 
//                "http://tusfleestedt.de/index.php?format=feed&type=rss";
//                "http://www.chip.de/rss/rss_technik.xml"
//                "https://statuscode.ch/feed.xml"
//                "http://www.oora.de/startseite/feed.rss"
//                "http://www.sportmediaset.mediaset.it/rss/homepage.xml"
//                "http://askldjd.com/feed/"
//                "https://www.nrdc.org/rss.xml"
                "http://ep00.epimg.net/rss/elpais/portada.xml"
                ;
        URL url = new URL(rssUrl);
        URLConnection con = url.openConnection();
        con.setRequestProperty("User-Agent", "Mozilla/5.0 (Macintosh; U; Intel Mac OS X 10.4; en-US; rv:1.9.2.2) Gecko/20100316 Firefox/3.6.2");
        InputStream in = decompressStream(con.getInputStream());
        String enc = con.getContentEncoding();
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
            System.out.println("checkArticle(\""+link+"\",\n" + "\""+title+"\",\n" +"\""+ d.text().substring(0, Math.min(100, d.text().length()))+"\",\n100,\n1,\nnew String[]{});");
        }
        
        
    }
    
    private void checkArticle(String articleUrl, String titleIndicator, String contentIndicator, int maxLength, int numberOfImgs, String[] excerpts) throws Exception {

        URL url = new URL(articleUrl);
        HttpURLConnection con = (HttpURLConnection)url.openConnection();
        con.setRequestProperty("User-Agent", "Mozilla/5.0 (Macintosh; U; Intel Mac OS X 10.4; en-US; rv:1.9.2.2) Gecko/20100316 Firefox/3.6.2");
        int status = con.getResponseCode();
        
        //following redirects if necessary
        int maxRedirects = 3;
        while ((status == HttpURLConnection.HTTP_MOVED_TEMP
                || status == HttpURLConnection.HTTP_MOVED_PERM
                || status == HttpURLConnection.HTTP_SEE_OTHER) && maxRedirects-->0) {
            String newUrl = con.getHeaderField("Location");
            con = (HttpURLConnection) new URL(newUrl).openConnection();
            con.setRequestProperty("User-Agent", "Mozilla/5.0 (Macintosh; U; Intel Mac OS X 10.4; en-US; rv:1.9.2.2) Gecko/20100316 Firefox/3.6.2");
            status = con.getResponseCode();
        }
        
        InputStream in = decompressStream(con.getInputStream());

        String s = ArticleTextExtractor.extractContent(in, contentIndicator, titleIndicator);
        Document doc = Jsoup.parse(s);

        System.out.println(articleUrl);
        String content = doc.text().replaceAll("(.{200})", "$1\n");
//        System.out.println(content);
        System.out.println(doc.text().length());
        System.out.println(doc.select("img").size());
//        //Print image sources
//        for (int i = 0; i < doc.select("img").size(); i++) {
//            Element e = doc.select("img").get(i);
//            String src = e.attr("src");
//            System.out.println("Image: " + src +": "+e.attr("alt"));
//        }

        try {
        assertThat("Extracted document is probably too long!", doc.text().length(), is(lessThanOrEqualTo(maxLength)));
        assertEquals("Wrong numbers of images within full content.", numberOfImgs, doc.select("img").size());
        for (String e : excerpts) {
            assertThat(doc.text(), containsString(e));    
        }
        if (doc.text().length() < maxLength) {
            System.out.println("###################### GOOD! Content shorter but still complete. ##################################");
        }
        }
        catch(AssertionError e) {
            //Show in browser
            File temp = File.createTempFile("fullArticle", ".html");
            System.out.println("temp file: " + temp.toURI().toURL());
            BufferedWriter output = new BufferedWriter(new FileWriter(temp));
            String mobilizedHtml = HtmlUtils.improveHtmlContent(s, NetworkUtils.getBaseUrl(articleUrl));
            output.write(mobilizedHtml);
            output.close();
            Runtime rTime = Runtime.getRuntime();
            String browser = "C:/Program Files/Internet Explorer/iexplore.exe ";
            Process pc = rTime.exec(browser + temp.toURI().toURL());
            pc.waitFor();
            throw e;
        }

        System.out.println("-------------------------------------------------------------------------------");
    }

    
}