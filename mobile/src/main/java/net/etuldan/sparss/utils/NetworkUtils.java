/**
 * spaRSS
 * <p/>
 * Copyright (c) 2015-2016 Arnaud Renaud-Goud
 * Copyright (c) 2012-2015 Frederic Julian
 * <p/>
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * <p/>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * <p/>
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package net.etuldan.sparss.utils;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.text.Html;
import android.util.Log;

import net.etuldan.sparss.Constants;
import net.etuldan.sparss.MainApplication;
import net.etuldan.sparss.provider.FeedData;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.URL;
import java.util.HashSet;
import java.util.List;

 public class NetworkUtils {
     private static final String TAG = "NetworkUtils"; 

    public static final File IMAGE_FOLDER_FILE = new File(MainApplication.getContext().getCacheDir(), "images/");
    public static final String IMAGE_FOLDER = IMAGE_FOLDER_FILE.getAbsolutePath() + '/';
    public static final String TEMP_PREFIX = "TEMP__";
    public static final String ID_SEPARATOR = "__";

    private static final String FILE_FAVICON = "/favicon.ico";
    private static final String PROTOCOL_SEPARATOR = "://";

    /* used e.g. by http://www.oora.de/startseite/feed.rss */
    public static final int HTTP_REDIRECT_TEMP = 307;

    private static final CookieManager COOKIE_MANAGER = new CookieManager() {{
        CookieHandler.setDefault(this);
    }};

    public static String getDownloadedOrDistantImageUrl(long entryId, String imgUrl) {
        File dlImgFile = new File(NetworkUtils.getDownloadedImagePath(entryId, imgUrl));
        if (dlImgFile.exists()) {
            return Uri.fromFile(dlImgFile).toString();
        } else {
            return imgUrl;
        }
    }

    public static String getDownloadedImagePath(long entryId, String imgUrl) {
        return IMAGE_FOLDER + entryId + ID_SEPARATOR + StringUtils.getMd5(imgUrl);
    }

    private static String getTempDownloadedImagePath(long entryId, String imgUrl) {
        return IMAGE_FOLDER + TEMP_PREFIX + entryId + ID_SEPARATOR + StringUtils.getMd5(imgUrl);
    }

    public static void downloadImage(long entryId, String imgUrl) throws IOException {
        String tempImgPath = getTempDownloadedImagePath(entryId, imgUrl);
        String finalImgPath = getDownloadedImagePath(entryId, imgUrl);

        if (!new File(tempImgPath).exists() && !new File(finalImgPath).exists()) {
            HttpURLConnection imgURLConnection = null;
            try {
                IMAGE_FOLDER_FILE.mkdir(); // create images dir

                // Compute the real URL (without "&eacute;", ...)
                String realUrl = Html.fromHtml(imgUrl).toString();
                imgURLConnection = setupConnection(new URL(realUrl));

                FileOutputStream fileOutput = new FileOutputStream(tempImgPath);
                InputStream inputStream = imgURLConnection.getInputStream();

                byte[] buffer = new byte[2048];
                int bufferLength;
                while ((bufferLength = inputStream.read(buffer)) > 0) {
                    fileOutput.write(buffer, 0, bufferLength);
                }
                fileOutput.flush();
                fileOutput.close();
                inputStream.close();

                new File(tempImgPath).renameTo(new File(finalImgPath));
            } catch (IOException e) {
                new File(tempImgPath).delete();
                throw e;
            } finally {
                if (imgURLConnection != null) {
                    imgURLConnection.disconnect();
                }
            }
        }
    }

     public static synchronized void deleteEntriesImagesCache(long keepDateBorderTime) {
        if (IMAGE_FOLDER_FILE.exists()) {

            // We need to exclude favorite entries images to this cleanup
            Cursor cursor = MainApplication.getContext().getContentResolver().query(FeedData.EntryColumns.FAVORITES_CONTENT_URI, FeedData.EntryColumns.PROJECTION_ID, null, null, null);
            if (cursor != null) {
                HashSet<Long> favIds = new HashSet<>();
                while (cursor.moveToNext()) {
                    favIds.add(cursor.getLong(0));
                }

                File[] files = IMAGE_FOLDER_FILE.listFiles();
                if (files != null) {
                    for (File file : files) {
                        if (file.lastModified() < keepDateBorderTime) {
                            boolean isAFavoriteEntryImage = false;
                            for (Long favId : favIds) {
                                if (file.getName().startsWith(favId + ID_SEPARATOR)) {
                                    isAFavoriteEntryImage = true;
                                    break;
                                }
                            }
                            if (!isAFavoriteEntryImage) {
                                file.delete();
                            }
                        }
                    }
                }
                cursor.close();
            }
        }
    }

    public static boolean needDownloadPictures() {
        String fetchPictureMode = PrefUtils.getString(PrefUtils.PRELOAD_IMAGE_MODE, Constants.FETCH_PICTURE_MODE_WIFI_ONLY_PRELOAD);

        boolean downloadPictures = false;
        if (PrefUtils.getBoolean(PrefUtils.DISPLAY_IMAGES, true)) {
            if (Constants.FETCH_PICTURE_MODE_ALWAYS_PRELOAD.equals(fetchPictureMode)) {
                downloadPictures = true;
            } else if (Constants.FETCH_PICTURE_MODE_WIFI_ONLY_PRELOAD.equals(fetchPictureMode)) {
                ConnectivityManager cm = (ConnectivityManager) MainApplication.getContext().getSystemService(Context.CONNECTIVITY_SERVICE);
                NetworkInfo ni = cm.getActiveNetworkInfo();
                if (ni != null && ni.getType() == ConnectivityManager.TYPE_WIFI) {
                    downloadPictures = true;
                }
            }
        }
        return downloadPictures;
    }

    public static String getBaseUrl(String link) {
        String baseUrl = link;
        int index = link.indexOf('/', 8); // this also covers https://
        if (index > -1) {
            baseUrl = link.substring(0, index);
        }

        return baseUrl;
    }

    public static byte[] getBytes(InputStream inputStream) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        byte[] buffer = new byte[4096];

        int n;
        while ((n = inputStream.read(buffer)) > 0) {
            output.write(buffer, 0, n);
        }

        byte[] result = output.toByteArray();

        output.close();
        inputStream.close();
        return result;
    }

    public static void retrieveFavicon(Context context, URL url, String id) {
        boolean success = false;
        HttpURLConnection iconURLConnection = null;

        try {
            iconURLConnection = setupConnection(new URL(url.getProtocol() + PROTOCOL_SEPARATOR + url.getHost() + FILE_FAVICON));

            byte[] iconBytes = getBytes(iconURLConnection.getInputStream());
            if (iconBytes != null && iconBytes.length > 0) {
                Bitmap bitmap = BitmapFactory.decodeByteArray(iconBytes, 0, iconBytes.length);
                if (bitmap != null) {
                    if (bitmap.getWidth() != 0 && bitmap.getHeight() != 0) {
                        ContentValues values = new ContentValues();
                        values.put(FeedData.FeedColumns.ICON, iconBytes);
                        context.getContentResolver().update(FeedData.FeedColumns.CONTENT_URI(id), values, null, null);
                        success = true;
                    }
                    bitmap.recycle();
                }
            }
        }catch (FileNotFoundException e) {
            Log.d(TAG, "FileNotFoundException: " + e.getMessage());
        } catch (Throwable ignored) {
            Log.e(TAG, "Exception", ignored);
        } finally {
            if (iconURLConnection != null) {
                iconURLConnection.disconnect();
            }
        }

        if (!success) {
            // no icon found or error
            ContentValues values = new ContentValues();
            values.putNull(FeedData.FeedColumns.ICON);
            context.getContentResolver().update(FeedData.FeedColumns.CONTENT_URI(id), values, null, null);
        }
    }

    public static HttpURLConnection setupConnection(String url, String cookieName, String cookieValue, String login, String password) throws IOException {
        String cookie = cookieName == null || cookieName.isEmpty() ? "" : cookieName + "=" + cookieValue;
        return setupConnection(new URL(url), cookie, login, password);
    }

    public static HttpURLConnection setupConnection(URL url) throws IOException {
        return setupConnection(url, "", "", "");
    }

    public static HttpURLConnection setupConnection(String url, String login, String password) throws IOException {
        return setupConnection(new URL(url), "", login, password);
    }

    public static HttpURLConnection setupConnection(URL url, String cookie, final String login, final String password) throws IOException {

        Proxy proxy = null;

        ConnectivityManager connectivityManager = (ConnectivityManager) MainApplication.getContext()
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        final NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();
        if (PrefUtils.getBoolean(PrefUtils.PROXY_ENABLED, false)
                && (networkInfo.getType() == ConnectivityManager.TYPE_WIFI || !PrefUtils.getBoolean(PrefUtils.PROXY_WIFI_ONLY, false))) {
            try {
                proxy = new Proxy("0".equals(PrefUtils.getString(PrefUtils.PROXY_TYPE, "0")) ? Proxy.Type.HTTP : Proxy.Type.SOCKS,
                        new InetSocketAddress(PrefUtils.getString(PrefUtils.PROXY_HOST, ""), Integer.parseInt(PrefUtils.getString(
                                PrefUtils.PROXY_PORT, "8080")))
                );
            } catch (Exception e) {
                proxy = null;
            }
        }

        if (proxy == null) {
            // Try to get the system proxy
            try {
                ProxySelector defaultProxySelector = ProxySelector.getDefault();
                List<Proxy> proxyList = defaultProxySelector.select(url.toURI());
                if (!proxyList.isEmpty()) {
                    proxy = proxyList.get(0);
                }
            } catch (Throwable ignored) {
                Log.e(TAG, "Exception", ignored);
            }
        }
        if (login!=null && password!=null && !password.equals("") && !login.equals("")) {
            Authenticator.setDefault(new Authenticator() {
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(login, password.toCharArray());
                }
            });
        }
        HttpURLConnection connection = null;
        CookieManager cookieManager = new CookieManager();
        CookieHandler.setDefault(cookieManager);
        int status = 0;
        boolean first = true;
        while(first || status == HttpURLConnection.HTTP_MOVED_TEMP || status == HttpURLConnection.HTTP_MOVED_PERM || 
                status == HttpURLConnection.HTTP_SEE_OTHER || status == HTTP_REDIRECT_TEMP) {
            if(!first) {
                url = new URL(connection.getHeaderField("Location"));
            } else {
                first = false;
            }
            connection = proxy == null ? (HttpURLConnection) url.openConnection() : (HttpURLConnection) url.openConnection(proxy);

            connection.setRequestProperty("Cookie", cookie);
            connection.setDoInput(true);
            connection.setDoOutput(false);
            connection.setRequestProperty("User-agent", "Mozilla/5.0 (compatible) AppleWebKit Chrome Safari"); // some feeds need this to work properly
            connection.setConnectTimeout(30000);
            connection.setReadTimeout(30000);
            connection.setUseCaches(false);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("accept", "*/*");

            COOKIE_MANAGER.getCookieStore().removeAll(); // Cookie is important for some sites, but we clean them each times

            connection.connect();
            status = connection.getResponseCode();
        }

        return connection;
    }
}
