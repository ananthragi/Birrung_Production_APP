package au.com.birrung.tv.Services;


import static android.content.Context.MODE_PRIVATE;

import android.annotation.SuppressLint;
import android.app.ProgressDialog;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.util.Log;
import android.widget.Toast;

import com.android.volley.AuthFailureError;
import com.android.volley.DefaultRetryPolicy;
import com.android.volley.NetworkResponse;
import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.Volley;
import au.com.birrung.tv.Activites.Interfaces.ApiSucessResponse;
import au.com.birrung.tv.Activites.Interfaces.CarteResponse;
import au.com.birrung.tv.Database.SqlLiteDb;

import au.com.birrung.tv.NetworkCalling.NetworkServiceCall;
import au.com.birrung.tv.NetworkCalling.OnServiceCallCompleteListener;
import au.com.birrung.tv.NetworkCalling.ServerURL;
import au.com.birrung.tv.NetworkCalling.VolleyMultipartRequest;
import au.com.birrung.tv.R;


import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;


public class ApiDataSync {
    SQLiteDatabase database;
    String DB_PATH, dbpath, DB_NAME, authcode, time = "", fcmid, uid;
    Context context;
    SharedPreferences userdata;
    CarteResponse cuteResponse;

    private ArrayList<String> privategroupdid;
    private ArrayList<String> publicgroupdid;
    String ipddress;
    ProgressDialog pDialog;
    ApiSucessResponse apiSucessResponse;
    CarteResponse carteResponse;
    byte[] adharDoc,panDoc;
    Uri uri = null;
    InputStream iStream = null;
    public ApiDataSync(Context context) {
        this.context = context;
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            DB_PATH = context.getApplicationInfo().dataDir + "/databases/";
        } else {
            DB_PATH = context.getFilesDir().getParentFile().getPath() + "/databases/";
        }
        dbpath = DB_PATH + DB_NAME;

        userdata = context.getSharedPreferences("Userdata", MODE_PRIVATE);
        authcode = userdata.getString("authToken", "");
        fcmid = userdata.getString("notificationId", null) + "";
        uid = userdata.getString("user_id", null) + "";
        pDialog = new ProgressDialog(context);
    }


    public byte[] getBytes(InputStream inputStream) throws IOException {
        ByteArrayOutputStream byteBuffer = new ByteArrayOutputStream();
        int bufferSize = 1024;
        byte[] buffer = new byte[bufferSize];

        int len = 0;
        while ((len = inputStream.read(buffer)) != -1) {
            byteBuffer.write(buffer, 0, len);
        }
        return byteBuffer.toByteArray();
    }
    public byte[] getFileDataFromDrawable(Bitmap bitmap) {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 30, byteArrayOutputStream);
        byte[] imageInByte = byteArrayOutputStream.toByteArray();
        long length = imageInByte.length;
       // Log.e("sizeofimage", String.valueOf(length));
        return byteArrayOutputStream.toByteArray();
    }

    public void LoginAPicall(String email, String pass, CarteResponse apiSucessResponse) {
        try {
            this.carteResponse = apiSucessResponse;
            String regdetails = ServerURL.Login();
            HashMap<String, String> hashMap = new HashMap<>();
            hashMap.put("sessioId", email);
            // The build this screen is running. The backend compares it against what that
            // screen should be on and answers with "version"/"apkPath" accordingly, which is
            // also what lets an update be aimed at particular screens rather than all of them.
            hashMap.put("currentVersion",
                    au.com.birrung.tv.signage.update.AppUpdateManager
                            .installedVersionName(context));
            // The screen's own clock at the moment Submit was pressed, e.g. "09:43 PM".
            // Locale.US so the AM/PM marker is always what the backend expects -- some
            // locales render it differently, or use a 24-hour marker entirely.
            hashMap.put("time_zone",
                    new SimpleDateFormat("hh:mm a", Locale.US).format(new Date()));
         //   Log.e("Logindetails", regdetails + "," + email + "," + pass);
            NetworkServiceCall serviceCall = new NetworkServiceCall(context, false);
            serviceCall.setOnServiceCallCompleteListener(new LogviceCallCompleteListener(email));
            serviceCall.makeJSONObjectPostStringRequest(regdetails, hashMap, Request.Priority.HIGH);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    /**
     * Tells the server this screen is logging out, so the backend can release the activation
     * code for use on another screen. Fire-and-forget: the device logs out locally whatever the
     * server replies, so a backend problem can never trap the user on the player screen.
     *
     * The response is logged under the "LogoutApi" tag for field testing.
     */
    public void LogoutApiCall(String sessionId) {
        try {
            if (sessionId == null || sessionId.trim().isEmpty()) {
                Log.e("LogoutApi", "skipped: no sessionId stored on this device");
                return;
            }
            String url = ServerURL.logout();
            HashMap<String, String> hashMap = new HashMap<>();
            // NB: screen_logout expects "sessionId" -- unlike /apklogin, which spells it
            // "sessioId". Confirmed against the backend; do not "correct" one to match the other.
            hashMap.put("sessionId", sessionId);
            Log.e("LogoutApi", "POST " + url + "  sessioId=" + sessionId);

            NetworkServiceCall serviceCall = new NetworkServiceCall(context, false);
            serviceCall.setOnServiceCallCompleteListener(new LogoutCallCompleteListener());
            serviceCall.makeJSONObjectPostStringRequest(url, hashMap, Request.Priority.HIGH);
        } catch (Exception e) {
            Log.e("LogoutApi", "request failed to start: " + e.getMessage());
        }
    }

    private class LogoutCallCompleteListener implements OnServiceCallCompleteListener {
        @Override
        public void onJSONObjectResponse(JSONObject jsonObject) {
            Log.e("LogoutApi", "response: " + jsonObject);
        }

        @Override
        public void onJSONArrayResponse(JSONArray jsonArray) {
            Log.e("LogoutApi", "response (array): " + jsonArray);
        }

        @Override
        public void onErrorResponse(VolleyError error) {
            String body = "";
            try {
                if (error != null && error.networkResponse != null) {
                    body = " http=" + error.networkResponse.statusCode
                            + " body=" + new String(error.networkResponse.data);
                }
            } catch (Exception ignored) { }
            Log.e("LogoutApi", "error: " + (error == null ? "null" : error.getMessage()) + body);
        }
    }

    public void deleteIDCart(String authcode, String deleteid, ApiSucessResponse apiSucessResponse) {
        try {

            this.apiSucessResponse = apiSucessResponse;
            String deleteCartItem = ServerURL.DeleteCartItem();
            HashMap<String, String> hashMap = new HashMap<>();
            hashMap.put("authToken", authcode);
            hashMap.put("cart_id", deleteid);

            NetworkServiceCall serviceCall = new NetworkServiceCall(context, true);
            serviceCall.setOnServiceCallCompleteListener(new deleteCartID());
          //  Log.e("deleteCartId", deleteCartItem + "," + authcode + "," + deleteid);
            serviceCall.makeJSONObjectPostStringRequest(deleteCartItem, hashMap, Request.Priority.HIGH);
            // delete total files in folder
            //dletefiles();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    public void MQTTAPicall(String sessionId, String currentTime, String deviceName, long availMem, long totalMem, CarteResponse apiSucessResponse) {
        try {



            this.carteResponse = apiSucessResponse;
            String regdetails = ServerURL.MqttScreens();
            HashMap<String, String> hashMap = new HashMap<>();
            hashMap.put("userid", sessionId);
            hashMap.put("time", currentTime);

            hashMap.put("deviceName", deviceName);
            hashMap.put("availableMem", String.valueOf(availMem));
            hashMap.put("totalMem", String.valueOf(totalMem));
            hashMap.put("mystatus", "1");
            Log.e("mqttupdate",regdetails+","+sessionId+","+currentTime+","+deviceName+","+availMem+","+totalMem);
            //   Log.e("Logindetails", regdetails + "," + email + "," + pass);
            NetworkServiceCall serviceCall = new NetworkServiceCall(context, false);
            serviceCall.setOnServiceCallCompleteListener(new MqttCallCompleteListener());
            serviceCall.makeJSONObjectPostStringRequest(regdetails, hashMap, Request.Priority.HIGH);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private class LogviceCallCompleteListener implements OnServiceCallCompleteListener {
        private String userid;
        public LogviceCallCompleteListener(String email) {
            this.userid=email;
        }

        @Override
        public void onJSONObjectResponse(JSONObject jsonObject) {
            try {
                Log.e("Logindetails", "" + jsonObject);
                String status = jsonObject.optString("status", "0");

                au.com.birrung.tv.signage.update.AppUpdateManager.check(
                        context, jsonObject.optString("version", ""),
                        jsonObject.optString("apkPath", ""));

                if (status.equals("1")) {
                    String offLine = jsonObject.optString("offLine", "0");
                    String mqttServerurl = jsonObject.optString("MQTT_URL", "");
                    SharedPreferences.Editor editor = userdata.edit();
                    editor.putString("sessionId", userid);
                    editor.putString("offline", offLine);
                    editor.putString("mqttServer", mqttServerurl);

                    JSONArray scheduleArray = jsonObject.optJSONArray("data"); // your API field
                    if (scheduleArray != null) {
                        editor.putString("schedule_json", scheduleArray.toString());
                    }
                    editor.apply();

                    carteResponse.apiStatus("1", offLine);

                } else {
                    // e.g. DUPLICATE_LOGIN → pass the server's message straight to the UI.
                    String message = jsonObject.optString("message",
                            jsonObject.optString("error", "Login failed. Please try again."));
                    carteResponse.apiStatus("0", message);
                }

            } catch (Exception e) {
                e.printStackTrace();
                carteResponse.apiStatus("0", "Login failed. Please try again.");
            }
        }

        @Override
        public void onJSONArrayResponse(JSONArray jsonArray) {

        }

        @Override
        public void onErrorResponse(VolleyError error) {
            // Ensure the UI (button state) recovers on a network/server error.
            carteResponse.apiStatus("0", "Network error. Please check your connection and try again.");
        }
    }

    private class deleteCartID implements OnServiceCallCompleteListener {
        @Override
        public void onJSONObjectResponse(JSONObject jsonObject) {
            try {
             //   Log.e("deleteCartId", jsonObject.toString());
                String status = jsonObject.getString("status");
                String errorcode = jsonObject.getString("errorCode");
                String message = jsonObject.getString("message");

                if (status.equals("1")) {

                    apiSucessResponse.apiStatus("1");
                } else {
                    apiSucessResponse.apiStatus("0");
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onJSONArrayResponse(JSONArray jsonArray) {

        }

        @Override
        public void onErrorResponse(VolleyError error) {

        }
    }

    private void InsertInotDB(String startdate, String timelist, int size, String status, String chairlist, int chaircount, String recorded) {
        if (size == 0) {
            dELETEDATE(startdate);
        }

        database = new SqlLiteDb(context).getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(SqlLiteDb.ctx_date, startdate);
        values.put(SqlLiteDb.ctx_times, timelist);
        values.put(SqlLiteDb.ctx_no_count, size);
        values.put(SqlLiteDb.ctx_status, status);
        values.put(SqlLiteDb.ctx_chairlist, chairlist);
        values.put(SqlLiteDb.ctx_chaircount, chaircount);
        values.put(SqlLiteDb.ctx_recoredId, recorded);

        database.replace(SqlLiteDb.PERSON_TABLE_NAME, null, values);
      //  Log.e("datesselected", startdate + "," + timelist + "," + size + "," + status + "," + chairlist + "," + chaircount + "," + recorded);

        database.close();


    }
    @SuppressLint("Range")
    private void dELETEDATE(String startdate) {
        database = SQLiteDatabase.openOrCreateDatabase(dbpath, null);
        String query = "SELECT * FROM bookingdates";
        database = new SqlLiteDb(context).getReadableDatabase();
        Cursor cursor = database.rawQuery(query, null);
        while (cursor.moveToNext()) {

            String messgegidid = (cursor.getString(cursor.getColumnIndex(SqlLiteDb.ctx_date)));

           // Log.e("pppppppp", messgegidid);
            if (startdate.equals(messgegidid)) {
                Log.e("remove", startdate + "," + messgegidid + ",,," + "yes");

                String query1 = "DELETE  FROM bookingdates WHERE _date ='" + messgegidid + "'";
                database.execSQL(query1);
               // Log.e("deletecorporate", query1);
            } else {
              //  Log.e("remove", startdate + "," + messgegidid + ",,," + "Notmaching");
            }


        }
        database.close();

    }

    private class MqttCallCompleteListener implements OnServiceCallCompleteListener {
        @Override
        public void onJSONObjectResponse(JSONObject jsonObject) {
            try {
                Log.e("mqttupdate", jsonObject.toString());
                String status = jsonObject.getString("status");
                String offLine = jsonObject.getString("offLine");
                String mqttServerurl = jsonObject.getString("MQTT_URL");

                JSONArray scheduleArray = jsonObject.getJSONArray("data"); // your API field
                SharedPreferences.Editor editor1 = userdata.edit();
                // This endpoint can return a PowerPoint deck without its slides (as type
                // "unknown format"), which would otherwise wipe out slides we already hold
                // and leave the screen empty. Keep the slides for a deck we already know.
                String scheduleJson = au.com.birrung.tv.signage.model.ContentSchedule
                        .preservePptSlides(scheduleArray, userdata.getString("schedule_json", ""));
                editor1.putString("schedule_json", scheduleJson);

                // The admin panel advertises the current build here. Returns immediately; the
                // download runs in the background and never holds up the schedule.
                au.com.birrung.tv.signage.update.AppUpdateManager.check(
                        context, jsonObject.optString("version", ""),
                        jsonObject.optString("apkPath", ""));
                editor1.putString("mqttServer", mqttServerurl);
                editor1.apply();
                editor1.commit();
                if (status.equals("1")) {
                    carteResponse.apiStatus("1", mqttServerurl);




                } else {
                    carteResponse.apiStatus("0", "No Play Found");
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        @Override
        public void onJSONArrayResponse(JSONArray jsonArray) {

        }

        @Override
        public void onErrorResponse(VolleyError error) {

        }
    }
}




