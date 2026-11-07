package com.globaltrio.app;

import android.Manifest;
import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ContentValues;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.IntentFilter;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.hardware.biometrics.BiometricManager;
import android.hardware.biometrics.BiometricPrompt;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.Settings;
import android.util.Base64;
import android.widget.Toast;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.net.URL;
import java.util.Iterator;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.ResponseBody;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class MainActivity extends Activity {
    private WebView web;
    private ValueCallback<Uri[]> fileCb;
    private Uri camUri;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        web = new WebView(this);
        setContentView(web);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        web.setWebViewClient(new WebViewClient());
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView w, ValueCallback<Uri[]> cb, FileChooserParams params) {
                if (fileCb != null) fileCb.onReceiveValue(null);
                fileCb = cb;
                camUri = null;
                if (params.isCaptureEnabled()) {
                    try {
                        ContentValues cv = new ContentValues();
                        cv.put(MediaStore.Images.Media.DISPLAY_NAME, "GlobalTrio_" + System.currentTimeMillis() + ".jpg");
                        cv.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
                        cv.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/GlobalTrio");
                        camUri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv);
                        Intent cam = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                        cam.putExtra(MediaStore.EXTRA_OUTPUT, camUri);
                        cam.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        startActivityForResult(cam, 1002);
                        return true;
                    } catch (Exception e) {
                        camUri = null;
                    }
                }
                try {
                    startActivityForResult(params.createIntent(), 1001);
                } catch (Exception e) {
                    fileCb = null;
                    return false;
                }
                return true;
            }
        });
        web.addJavascriptInterface(new Bridge(), "Native");
        IntentFilter filter = new IntentFilter(ACTION_INSTALL_DONE);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(installReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(installReceiver, filter);
        web.loadUrl("file:///android_asset/index.html");
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        if (req == 1002) {
            if (fileCb != null) {
                if (res == RESULT_OK && camUri != null) fileCb.onReceiveValue(new Uri[]{camUri});
                else {
                    if (camUri != null) try { getContentResolver().delete(camUri, null, null); } catch (Exception e) {}
                    fileCb.onReceiveValue(null);
                }
                fileCb = null;
            }
            camUri = null;
            return;
        }
        if (req == 1001) {
            if (fileCb != null) {
                fileCb.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(res, data));
                fileCb = null;
            }
            return;
        }
        super.onActivityResult(req, res, data);
    }

    @Override
    public void onBackPressed() {
        if (web.canGoBack()) web.goBack(); else super.onBackPressed();
    }

    private Uri storeFile(String name, String b64, String mime) throws Exception {
        byte[] data = Base64.decode(b64, Base64.DEFAULT);
        ContentValues v = new ContentValues();
        v.put(MediaStore.Downloads.DISPLAY_NAME, name);
        v.put(MediaStore.Downloads.MIME_TYPE, mime);
        v.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
        Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
        OutputStream os = getContentResolver().openOutputStream(uri);
        os.write(data);
        os.close();
        return uri;
    }

    private static final String ACTION_INSTALL_DONE = "com.globaltrio.app.INSTALL_DONE";

    private void js(final String code) {
        runOnUiThread(new Runnable() {
            public void run() { web.evaluateJavascript(code, null); }
        });
    }

    private final BroadcastReceiver installReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            int status = i.getIntExtra(PackageInstaller.EXTRA_STATUS, -1);
            String msg = i.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
            if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
                Intent confirm = (Intent) i.getParcelableExtra(Intent.EXTRA_INTENT);
                if (confirm != null) {
                    confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(confirm);
                }
                js("window.__updi()");
            } else if (status == PackageInstaller.STATUS_SUCCESS) {
                js("window.__upd(true)");
            } else {
                String why = "The update could not be installed.";
                if (status == PackageInstaller.STATUS_FAILURE_CONFLICT || status == PackageInstaller.STATUS_FAILURE_INCOMPATIBLE) {
                    why = "Android refused the update because it is signed differently from the installed app.";
                } else if (status == PackageInstaller.STATUS_FAILURE_ABORTED) {
                    why = "The install was cancelled.";
                } else if (status == PackageInstaller.STATUS_FAILURE_STORAGE) {
                    why = "Not enough free space on the phone to install the update.";
                } else if (msg != null && msg.length() > 0) {
                    why = "The update could not be installed: " + msg;
                }
                js("window.__upd(false," + JSONObject.quote(why) + ")");
            }
        }
    };

    private void startInstall(File apk) throws Exception {
        if (!getPackageManager().canRequestPackageInstalls()) {
            startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + getPackageName())));
            js("window.__upd(false," + JSONObject.quote("Allow \"Install unknown apps\" for Global Trio on the screen that just opened, then come back and tap Update again.") + ")");
            return;
        }
        PackageInstaller pi = getPackageManager().getPackageInstaller();
        PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        int id = pi.createSession(params);
        PackageInstaller.Session session = pi.openSession(id);
        try {
            InputStream in = new FileInputStream(apk);
            OutputStream out = session.openWrite("update.apk", 0, apk.length());
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            session.fsync(out);
            out.close();
            in.close();
            Intent done = new Intent(ACTION_INSTALL_DONE).setPackage(getPackageName());
            PendingIntent pend = PendingIntent.getBroadcast(this, id, done, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
            session.commit(pend.getIntentSender());
        } finally {
            session.close();
        }
    }

    private void startBiometric(final String title, final String negative, final boolean report) {
        runOnUiThread(new Runnable() {
            public void run() {
                try {
                    BiometricPrompt prompt = new BiometricPrompt.Builder(MainActivity.this)
                            .setTitle(title)
                            .setNegativeButton(negative, getMainExecutor(), new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface d, int which) {
                                    if (report) web.evaluateJavascript("window.__bio(false)", null);
                                }
                            })
                            .build();
                    prompt.authenticate(new CancellationSignal(), getMainExecutor(), new BiometricPrompt.AuthenticationCallback() {
                        @Override
                        public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) {
                            web.evaluateJavascript("window.__bio(true)", null);
                        }
                        @Override
                        public void onAuthenticationError(int code, CharSequence msg) {
                            if (report) web.evaluateJavascript("window.__bio(false)", null);
                        }
                        @Override
                        public void onAuthenticationFailed() { }
                    });
                } catch (Exception e) {
                    if (report) web.evaluateJavascript("window.__bio(false)", null);
                }
            }
        });
    }

    /** Lets the page call Google/Microsoft APIs without browser CORS limits, and open the sign-in page. */
    class Bridge {
        @JavascriptInterface
        public void openUrl(final String url) {
            runOnUiThread(new Runnable() {
                public void run() {
                    try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); } catch (Exception e) { }
                }
            });
        }

        @JavascriptInterface
        public void copy(final String text) {
            runOnUiThread(new Runnable() {
                public void run() {
                    ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                    cm.setPrimaryClip(ClipData.newPlainText("code", text));
                }
            });
        }

        @JavascriptInterface
        public void saveFile(final String name, final String b64, final String mime) {
            runOnUiThread(new Runnable() {
                public void run() {
                    try {
                        Uri uri = storeFile(name, b64, mime);
                        Toast.makeText(MainActivity.this, "Saved to Downloads: " + name, Toast.LENGTH_LONG).show();
                        Intent send = new Intent(Intent.ACTION_SEND);
                        send.setType(mime);
                        send.putExtra(Intent.EXTRA_STREAM, uri);
                        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        startActivity(Intent.createChooser(send, "Send to OneDrive / Drive / WhatsApp"));
                    } catch (Exception e) {
                        Toast.makeText(MainActivity.this, "Could not save: " + e, Toast.LENGTH_LONG).show();
                    }
                }
            });
        }

        @JavascriptInterface
        public void emailFile(final String name, final String b64, final String mime, final String to, final String subject) {
            runOnUiThread(new Runnable() {
                public void run() {
                    try {
                        Uri uri = storeFile(name, b64, mime);
                        Intent send = new Intent(Intent.ACTION_SEND);
                        send.setType(mime);
                        send.putExtra(Intent.EXTRA_EMAIL, new String[]{to});
                        send.putExtra(Intent.EXTRA_SUBJECT, subject);
                        send.putExtra(Intent.EXTRA_TEXT, "Global Trio data is attached.");
                        send.putExtra(Intent.EXTRA_STREAM, uri);
                        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        startActivity(Intent.createChooser(send, "Email to " + to));
                    } catch (Exception e) {
                        Toast.makeText(MainActivity.this, "Could not prepare email: " + e, Toast.LENGTH_LONG).show();
                    }
                }
            });
        }

        @JavascriptInterface
        public void saveQuiet(final String name, final String b64, final String mime) {
            runOnUiThread(new Runnable() {
                public void run() {
                    try {
                        storeFile(name, b64, mime);
                    } catch (Exception e) {
                        Toast.makeText(MainActivity.this, "Backup failed: " + e, Toast.LENGTH_LONG).show();
                    }
                }
            });
        }

        @JavascriptInterface
        public void shareText(final String text) {
            runOnUiThread(new Runnable() {
                public void run() {
                    Intent send = new Intent(Intent.ACTION_SEND);
                    send.setType("text/plain");
                    send.putExtra(Intent.EXTRA_TEXT, text);
                    startActivity(Intent.createChooser(send, "Share"));
                }
            });
        }

        @JavascriptInterface
        public void askNotify() {
            runOnUiThread(new Runnable() {
                public void run() {
                    if (Build.VERSION.SDK_INT >= 33
                            && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                        requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 77);
                    }
                }
            });
        }

        @JavascriptInterface
        public void pushNotify(final String title, final String text) {
            runOnUiThread(new Runnable() {
                public void run() {
                    try {
                        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                        nm.createNotificationChannel(new NotificationChannel("stock", "Stock alerts", NotificationManager.IMPORTANCE_DEFAULT));
                        PendingIntent pi = PendingIntent.getActivity(MainActivity.this, 0, new Intent(MainActivity.this, MainActivity.class),
                                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
                        Notification n = new Notification.Builder(MainActivity.this, "stock")
                                .setSmallIcon(getApplicationInfo().icon)
                                .setContentTitle(title)
                                .setContentText(text)
                                .setContentIntent(pi)
                                .setAutoCancel(true)
                                .build();
                        nm.notify((int) (System.currentTimeMillis() / 1000), n);
                    } catch (Exception e) { }
                }
            });
        }

        @JavascriptInterface
        public boolean canBiometric() {
            try {
                BiometricManager bm = getSystemService(BiometricManager.class);
                return bm != null && bm.canAuthenticate() == BiometricManager.BIOMETRIC_SUCCESS;
            } catch (Exception e) {
                return false;
            }
        }

        @JavascriptInterface
        public void biometric() {
            startBiometric("Unlock Global Trio", "Use PIN", false);
        }

        @JavascriptInterface
        public void biometricAuth(final String title) {
            startBiometric(title, "Cancel", true);
        }

        @JavascriptInterface
        public void installUpdate(final String url, final String sha, final long size, final String version) {
            if (url == null || !url.startsWith("https://github.com/") || !url.endsWith("/GlobalTrio.apk")
                    || sha == null || !sha.matches("[0-9a-f]{64}") || size < 100000 || size > 100L * 1024 * 1024) {
                js("window.__upd(false," + JSONObject.quote("The update link was not accepted.") + ")");
                return;
            }
            new Thread(new Runnable() {
                public void run() {
                    File apk = new File(getCacheDir(), "update.apk");
                    try {
                        OkHttpClient client = new OkHttpClient.Builder()
                                .followRedirects(true)
                                .connectTimeout(30, TimeUnit.SECONDS)
                                .readTimeout(120, TimeUnit.SECONDS)
                                .build();
                        Response resp = client.newCall(new Request.Builder().url(url).build()).execute();
                        if (resp.code() != 200) {
                            resp.close();
                            js("window.__upd(false," + JSONObject.quote("Could not download the update (error " + resp.code() + ").") + ")");
                            return;
                        }
                        ResponseBody body = resp.body();
                        long total = body.contentLength() > 0 ? body.contentLength() : size;
                        MessageDigest md = MessageDigest.getInstance("SHA-256");
                        InputStream in = body.byteStream();
                        FileOutputStream out = new FileOutputStream(apk);
                        byte[] buf = new byte[65536];
                        long got = 0;
                        int n, lastPct = -1;
                        while ((n = in.read(buf)) > 0) {
                            out.write(buf, 0, n);
                            md.update(buf, 0, n);
                            got += n;
                            if (got > 100L * 1024 * 1024) throw new Exception("The file is too large");
                            int pct = (int) Math.min(100, got * 100 / total);
                            if (pct != lastPct && pct % 3 == 0) {
                                lastPct = pct;
                                js("window.__updp(" + pct + ")");
                            }
                        }
                        out.close();
                        in.close();
                        resp.close();
                        StringBuilder hex = new StringBuilder();
                        for (byte b : md.digest()) hex.append(String.format("%02x", b));
                        if (!hex.toString().equals(sha) || (size > 0 && got != size)) {
                            apk.delete();
                            js("window.__upd(false," + JSONObject.quote("The downloaded file did not match the published update (damaged or changed). Nothing was installed. Try again.") + ")");
                            return;
                        }
                        final File ready = apk;
                        runOnUiThread(new Runnable() {
                            public void run() {
                                try {
                                    startInstall(ready);
                                } catch (Exception e) {
                                    js("window.__upd(false," + JSONObject.quote("Could not start the install: " + e) + ")");
                                }
                            }
                        });
                    } catch (Exception e) {
                        apk.delete();
                        js("window.__upd(false," + JSONObject.quote("Update download failed: " + e) + ")");
                    }
                }
            }).start();
        }

        @JavascriptInterface
        public void request(final String id, final String method, final String url, final String headers, final String body) {
            new Thread(new Runnable() {
                public void run() {
                    int status = 0;
                    String text = "";
                    try {
                        OkHttpClient client = new OkHttpClient.Builder()
                                .followRedirects(false)
                                .connectTimeout(30, TimeUnit.SECONDS)
                                .readTimeout(60, TimeUnit.SECONDS)
                                .writeTimeout(60, TimeUnit.SECONDS)
                                .build();
                        JSONObject h = new JSONObject(headers);
                        boolean isB64 = h.has("X-GT-Base64");
                        if (isB64) h.remove("X-GT-Base64");
                        String u = url;
                        boolean auth = true;
                        for (int hop = 0; hop < 5; hop++) {
                            Request.Builder rb = new Request.Builder().url(u);
                            String ct = null;
                            Iterator<String> it = h.keys();
                            while (it.hasNext()) {
                                String k = it.next();
                                if (k.equalsIgnoreCase("Content-Type")) { ct = h.getString(k); continue; }
                                if (!auth && k.equalsIgnoreCase("Authorization")) continue;
                                rb.header(k, h.getString(k));
                            }
                            boolean needsBody = method.equals("POST") || method.equals("PUT") || method.equals("PATCH");
                            RequestBody reqBody = null;
                            if (needsBody) {
                                byte[] bytes = body == null ? new byte[0] : (isB64 ? Base64.decode(body, Base64.DEFAULT) : body.getBytes("UTF-8"));
                                reqBody = RequestBody.create(bytes, ct == null ? null : MediaType.parse(ct));
                            }
                            rb.method(method, reqBody);
                            Response resp = client.newCall(rb.build()).execute();
                            status = resp.code();
                            String loc = resp.header("Location");
                            if (status >= 300 && status < 400 && loc != null && method.equals("GET")) {
                                u = new URL(new URL(u), loc).toString();
                                auth = false; // pre-authorised download links must not get our bearer token
                                resp.close();
                                continue;
                            }
                            text = resp.body() == null ? "" : resp.body().string();
                            resp.close();
                            break;
                        }
                    } catch (Exception e) {
                        status = 0;
                        text = String.valueOf(e);
                    }
                    final int st = status;
                    final String tx = text;
                    runOnUiThread(new Runnable() {
                        public void run() {
                            web.evaluateJavascript("window.__nr(" + JSONObject.quote(id) + "," + st + "," + JSONObject.quote(tx) + ")", null);
                        }
                    });
                }
            }).start();
        }
    }
}
