package com.ewa.admin;

import android.app.Activity;
import android.content.*;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.security.keystore.KeyStore.SecretKeyEntry;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import android.util.Base64;

public class MainActivity extends Activity {
    private static final String API = "/wp-json/ewa/v1";
    private static final String KEY_ALIAS = "EWA_ADMIN_TOKEN_KEY";
    private SharedPreferences prefs;
    private LinearLayout root, content;
    private TextView status, pageTitle;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences("ewa_admin", MODE_PRIVATE);
        if (!readToken().isEmpty() && !cleanBase(prefs.getString("base", "")).isEmpty()) showApp(); else showLogin();
    }

    private void showLogin() {
        root = base();
        LinearLayout box = cardBox("EWA Admin", "Administrator Control Center");
        EditText base = input("WordPress HTTPS URL", false); base.setText(prefs.getString("base", ""));
        EditText username = input("Administrator username", false);
        EditText password = input("Password", true);
        box.addView(base); box.addView(username); box.addView(password);
        Button sign = button("Sign in"); box.addView(sign);
        status = text(""); box.addView(status);
        sign.setOnClickListener(v -> {
            String server = cleanBase(base.getText().toString());
            String user = username.getText().toString().trim();
            String pass = password.getText().toString();
            if (server.isEmpty() || user.isEmpty() || pass.isEmpty()) { status.setText("Server URL, username and password are required."); return; }
            if (!server.startsWith("https://")) { status.setText("Use an HTTPS WordPress URL."); return; }
            status.setText("Signing in...");
            new Thread(() -> {
                try {
                    JSONObject body = new JSONObject().put("username", user).put("password", pass).put("device_label", "EWA Admin Android");
                    JSONObject r = new JSONObject(request(server + API + "/admin/app/login", "POST", body.toString(), null));
                    String token = r.optString("token", ""); if (token.isEmpty()) throw new IOException("Server did not return an administrator token.");
                    writeToken(token);
                    prefs.edit().putString("base", server).apply();
                    runOnUiThread(this::showApp);
                } catch (Exception e) { runOnUiThread(() -> status.setText("Sign-in failed: " + safeError(e))); }
            }).start();
        });
        root.addView(box); setContentView(root);
    }

    private void showApp() {
        root = base();
        LinearLayout header = new LinearLayout(this); header.setOrientation(LinearLayout.HORIZONTAL); header.setGravity(Gravity.CENTER_VERTICAL);
        pageTitle = title("EWA Admin"); header.addView(pageTitle, new LinearLayout.LayoutParams(0, -2, 1));
        Button logout = button("Sign out"); header.addView(logout); logout.setOnClickListener(v -> logout()); root.addView(header);
        status = text(""); root.addView(status);
        HorizontalScrollView navScroll = new HorizontalScrollView(this);
        LinearLayout nav = new LinearLayout(this); nav.setOrientation(LinearLayout.HORIZONTAL);
        String[] labels = {"Dashboard", "Students", "Memberships", "Courses", "Orders", "Analytics", "Notifications", "WhatsApp"};
        for (String label : labels) { Button b = button(label); nav.addView(b); b.setOnClickListener(v -> open(label)); }
        navScroll.addView(nav); root.addView(navScroll);
        ScrollView scroll = new ScrollView(this); content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL); scroll.addView(content); root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root); open("Dashboard");
    }

    private void open(String section) {
        content.removeAllViews(); status.setText(""); pageTitle.setText(section);
        switch(section) {
            case "Dashboard": loadOverview(); break;
            case "Students": loadStudents(); break;
            case "Memberships": loadMemberships(); break;
            case "Courses": loadCourses(); break;
            case "Orders": loadOrders(); break;
            case "Analytics": loadAnalytics(); break;
            case "Notifications": loadNotifications(); break;
            case "WhatsApp": loadWhatsApp(); break;
        }
    }

    private void loadOverview() {
        fetch("/admin/app/overview", obj -> { JSONObject j=asObject(obj); if(j==null)return; addMetric("Students",j.optInt("students")); addMetric("Active members",j.optInt("members")); addMetric("Pending memberships",j.optInt("pending_memberships")); addMetric("Orders",j.optInt("orders")); addMetric("WhatsApp queue",j.optInt("whatsapp_queue")); });
    }
    private void loadStudents() {
        LinearLayout row=new LinearLayout(this); EditText q=input("Search students",false); Button go=button("Search"); row.addView(q,new LinearLayout.LayoutParams(0,-2,1));row.addView(go);content.addView(row);
        Runnable load=()->fetch("/admin/app/students?limit=100&q="+enc(q.getText().toString()),obj->{content.removeViews(1,Math.max(0,content.getChildCount()-1));JSONArray a=asArray(obj);if(a!=null)for(int i=0;i<a.length();i++)addStudent(a.optJSONObject(i));}); go.setOnClickListener(v->load.run());load.run();
    }
    private void addStudent(JSONObject x){if(x==null)return;content.addView(card(x.optString("name","Student"),"Email: "+x.optString("email","—")+"\nClass: "+x.optString("class_name","—")+"\nWhatsApp: "+x.optString("whatsapp","—")+"\nState: "+x.optString("account_state","—")));}
    private void loadMemberships(){fetch("/admin/app/memberships?limit=100",obj->{JSONArray a=asArray(obj);if(a!=null)for(int i=0;i<a.length();i++){JSONObject x=a.optJSONObject(i);if(x!=null)content.addView(card(x.optString("name","Student"),"Status: "+x.optString("status","—")+"\nPayment reference: "+x.optString("payment_reference","—")));}});}
    private void loadCourses(){fetch("/admin/app/courses",obj->{JSONArray a=asArray(obj);if(a!=null)for(int i=0;i<a.length();i++){JSONObject x=a.optJSONObject(i);if(x!=null)content.addView(card(x.optString("name","Course"),"Class: "+x.optString("class_name","—")+"\nLessons: "+x.optInt("lesson_count")+"\nPublished: "+(x.optInt("published")==1?"Yes":"No")));}});}
    private void loadOrders(){fetch("/admin/app/orders?limit=100",obj->{JSONArray a=asArray(obj);if(a!=null)for(int i=0;i<a.length();i++){JSONObject x=a.optJSONObject(i);if(x!=null)content.addView(card(x.optString("order_code","Order"),"Customer: "+x.optString("user_name",x.optString("name","—"))+"\nAmount: "+x.optString("amount","0")+"\nStatus: "+x.optString("status","—")));}});}
    private void loadAnalytics(){fetch("/admin/app/analytics",obj->{JSONObject j=asObject(obj);if(j==null)return;for(String k:new String[]{"students","active_members","orders","revenue","quiz_attempts","average_quiz_score","course_completions","active_learners"})if(j.has(k))addMetric(k.replace('_',' '),j.opt(k));});}
    private void loadNotifications(){fetch("/admin/app/notifications",obj->{JSONArray a=asArray(obj);if(a!=null)for(int i=0;i<a.length();i++){JSONObject x=a.optJSONObject(i);if(x!=null)content.addView(card(x.optString("title","Notification"),x.optString("message","")+"\nType: "+x.optString("type","—")+"\n"+x.optString("created_at","")));}});}
    private void loadWhatsApp(){Button create=button("Create WhatsApp Message");content.addView(create);create.setOnClickListener(v->showMessageComposer());fetch("/admin/app/queue?limit=100",obj->{JSONArray a=asArrayField(obj,"items");if(a!=null)for(int i=0;i<a.length();i++)addQueue(a.optJSONObject(i));});}
    private void addQueue(JSONObject x){if(x==null)return;LinearLayout c=cardBox(x.optString("recipient",""),x.optString("message",""));Button open=button("Open in WhatsApp Business");c.addView(open);open.setOnClickListener(v->openWhatsApp(x));Button sent=button("Mark Sent");c.addView(sent);sent.setOnClickListener(v->transition(x.optInt("id"),"sent"));Button failed=button("Mark Failed");c.addView(failed);failed.setOnClickListener(v->transition(x.optInt("id"),"failed"));content.addView(c);}
    private void showMessageComposer(){LinearLayout box=cardBox("Create WhatsApp Message","The message will be queued. Sending remains a manual action in WhatsApp Business.");EditText r=input("Recipient WhatsApp number",false),m=input("Message",false);m.setMinLines(4);box.addView(r);box.addView(m);Button q=button("Add to Queue");box.addView(q);content.addView(box);q.setOnClickListener(v->{try{JSONObject b=new JSONObject().put("recipient",r.getText().toString()).put("message",m.getText().toString()).put("type","GENERAL");post("/admin/app/queue",b,o->open("WhatsApp"));}catch(Exception e){status.setText("Could not create message.");}});}
    private void openWhatsApp(JSONObject x){try{String p=x.optString("recipient","").replaceAll("[^0-9]","");String m=enc(x.optString("message",""));Intent i=new Intent(Intent.ACTION_VIEW,Uri.parse("https://wa.me/"+p+"?text="+m));i.setPackage("com.whatsapp.w4b");startActivity(i);transition(x.optInt("id"),"opened");}catch(Exception e){status.setText("WhatsApp Business is not available on this device.");}}
    private void transition(int id,String state){post("/admin/app/queue/"+id+"/"+state,new JSONObject(),o->open("WhatsApp"));}
    private void logout(){post("/admin/app/logout",new JSONObject(),o->{prefs.edit().clear().apply();showLogin();});}

    private void fetch(String path,Callback cb){status.setText("Loading...");new Thread(()->{try{String token=readToken();if(token.isEmpty())throw new IOException("Administrator session expired. Please sign in again.");Object j=parse(request(cleanBase(prefs.getString("base",""))+API+path,"GET",null,token));runOnUiThread(()->{status.setText("");cb.ok(j);});}catch(Exception e){runOnUiThread(()->status.setText("Request failed: "+safeError(e)));}}).start();}
    private void post(String path,JSONObject body,Callback cb){new Thread(()->{try{String token=readToken();if(token.isEmpty())throw new IOException("Administrator session expired. Please sign in again.");Object j=parse(request(cleanBase(prefs.getString("base",""))+API+path,"POST",body.toString(),token));runOnUiThread(()->cb.ok(j));}catch(Exception e){runOnUiThread(()->status.setText("Request failed: "+safeError(e)));}}).start();}
    interface Callback{void ok(Object j);} private Object parse(String s)throws Exception{return new JSONTokener(s).nextValue();} private JSONObject asObject(Object o){return o instanceof JSONObject?(JSONObject)o:null;} private JSONArray asArray(Object o){return o instanceof JSONArray?(JSONArray)o:null;} private JSONArray asArrayField(Object o,String k){JSONObject j=asObject(o);return j==null?null:j.optJSONArray(k);}
    private String request(String url,String method,String body,String token)throws Exception{HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();c.setRequestMethod(method);c.setConnectTimeout(15000);c.setReadTimeout(20000);c.setRequestProperty("Accept","application/json");if(token!=null&&!token.isEmpty())c.setRequestProperty("Authorization","Bearer "+token);if(body!=null){c.setDoOutput(true);c.setRequestProperty("Content-Type","application/json");try(OutputStream o=c.getOutputStream()){o.write(body.getBytes(StandardCharsets.UTF_8));}}int code=c.getResponseCode();InputStream in=code>=400?c.getErrorStream():c.getInputStream();StringBuilder s=new StringBuilder();if(in!=null)try(BufferedReader r=new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8))){String line;while((line=r.readLine())!=null)s.append(line);}if(code>=400)throw new IOException("HTTP "+code+": "+s);return s.toString();}
    private SecretKey getOrCreateKey()throws Exception{android.security.keystore.KeyStore ks=android.security.keystore.KeyStore.getInstance("AndroidKeyStore");ks.load(null);if(ks.containsAlias(KEY_ALIAS))return ((SecretKeyEntry)ks.getEntry(KEY_ALIAS,null)).getSecretKey();KeyGenerator kg=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");kg.init(new KeyGenParameterSpec.Builder(KEY_ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setRandomizedEncryptionRequired(true).build());return kg.generateKey();}
    private void writeToken(String token){try{Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.ENCRYPT_MODE,getOrCreateKey());prefs.edit().putString("token_iv",Base64.encodeToString(c.getIV(),Base64.NO_WRAP)).putString("token_ct",Base64.encodeToString(c.doFinal(token.getBytes(StandardCharsets.UTF_8)),Base64.NO_WRAP)).apply();}catch(Exception e){throw new IllegalStateException("Unable to secure administrator token.",e);}}
    private String readToken(){try{String ivs=prefs.getString("token_iv",""),cts=prefs.getString("token_ct","");if(ivs.isEmpty()||cts.isEmpty())return "";Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.DECRYPT_MODE,getOrCreateKey(),new GCMParameterSpec(128,Base64.decode(ivs,Base64.NO_WRAP)));return new String(c.doFinal(Base64.decode(cts,Base64.NO_WRAP)),StandardCharsets.UTF_8);}catch(Exception e){return "";}}
    private String cleanBase(String s){s=s.trim();while(s.endsWith("/"))s=s.substring(0,s.length()-1);return s;} private String enc(String s){try{return URLEncoder.encode(s,"UTF-8");}catch(Exception e){return s;}} private String safeError(Exception e){return e.getMessage()==null?"Unknown error":e.getMessage();}
    private LinearLayout base(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);l.setPadding(24,24,24,24);return l;} private LinearLayout cardBox(String h,String body){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);l.setPadding(16,16,16,16);TextView a=title(h);l.addView(a);if(!body.isEmpty())l.addView(text(body));return l;} private TextView title(String s){TextView t=text(s);t.setTextSize(24);t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t;} private TextView text(String s){TextView t=new TextView(this);t.setText(s);t.setTextSize(16);t.setPadding(0,8,0,8);return t;} private EditText input(String hint,boolean password){EditText e=new EditText(this);e.setHint(hint);if(password)e.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);e.setPadding(0,8,0,8);return e;} private Button button(String s){Button b=new Button(this);b.setText(s);return b;} private LinearLayout card(String h,String body){return cardBox(h,body);} private void addMetric(String k,Object v){content.addView(card(k,String.valueOf(v)));}
}
