package com.cilpl.Cynergytx_admin.Services;


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
import com.cilpl.Cynergytx_admin.Activites.Interfaces.ApiSucessResponse;
import com.cilpl.Cynergytx_admin.Activites.Interfaces.CarteResponse;
import com.cilpl.Cynergytx_admin.Database.SqlLiteDb;

import com.cilpl.Cynergytx_admin.NetworkCalling.NetworkServiceCall;
import com.cilpl.Cynergytx_admin.NetworkCalling.OnServiceCallCompleteListener;
import com.cilpl.Cynergytx_admin.NetworkCalling.ServerURL;
import com.cilpl.Cynergytx_admin.NetworkCalling.VolleyMultipartRequest;
import com.cilpl.Cynergytx_admin.R;


import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
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


  //  public void SendStatus(String fname, String lname, String email, String mob, String compnyname, String gstNo, String pass1, String pass2, String deviceid, String device_type, String ipaddress, CarteResponse apiSucessResponse, String gender, String dob, Bitmap aadharimg, Bitmap panimg,String adharstatus,String panstatus,String adhardoc,String pandoc) {
    public void SignUp(String fname, String lname, String email, String mob,
                           String compnyname, String gstNo, String pass1, String pass2,
                           String deviceid, String device_type, String ipaddress, CarteResponse apiSucessResponse, String gender, String dob) {
      //  Log.e("Notificationststus", "status Success" + "," + userdata.getString("item", ""));

       // Log.e("iiiiiiiiiii", panstatus+","+adharstatus);
//        try {
//            pDialog = new ProgressDialog(context);
//            pDialog.setMessage("Uploading...");
//            pDialog.setCancelable(false);
//            pDialog.getWindow().setBackgroundDrawable(new ColorDrawable(android.graphics.Color.TRANSPARENT));
//            pDialog.setIndeterminate(true);
//            pDialog.setCanceledOnTouchOutside(false);
//            pDialog.show();
//            pDialog.setContentView(R.layout.my_network_progress);
//
//
//
//            if(adharstatus.equals("2")){
//                uri = Uri.fromFile(new File((adhardoc)));
//                iStream = context.getContentResolver().openInputStream(uri);
//                assert iStream != null;
//                adharDoc = getBytes(iStream);
//
//            }
//            if(panstatus.equals("2")){
//                uri = Uri.fromFile(new File((pandoc)));
//                iStream = context.getContentResolver().openInputStream(uri);
//                assert iStream != null;
//                panDoc = getBytes(iStream);
//
//            }
////            if (!frontImagePath.isEmpty()) {
////                uri = Uri.fromFile(new File((frontImagePath)));
////                iStream = context.getContentResolver().openInputStream(uri);
////                assert iStream != null;
////                inputData = getBytes(iStream);
////            }
//            VolleyMultipartRequest volleyMultipartRequest = new VolleyMultipartRequest(Request.Method.POST, ServerURL.userregsiration(), new Response.Listener<NetworkResponse>() {
//                @Override
//                public void onResponse(NetworkResponse response) {
//                    try {
//
//                        JSONObject obj = new JSONObject(new String(response.data));
//                        Log.e("signupresponse", obj.toString());
//                        String status = obj.getString("status");
//                        String errorCode = obj.getString("errorCode");
//                        String message = obj.getString("message");
//                        pDialog.dismiss();
//                       // Log.e("changeimage", "" + status + "," + errorCode + "," + message);
//                        if (status.equals("1")) {
//                            String otp = obj.getString("OTP");
//                            String authtokenmessage = obj.getString("authToken");
//                            String uid = obj.getString("id");
//                            SharedPreferences.Editor editor = userdata.edit();
//                            editor.putString("authToken", authtokenmessage);
////                            editor.putString("userId", uid);
//                            editor.putString("otp", otp);
//                            editor.apply();
//                            apiSucessResponse.apiStatus("1",message);
//                        } else {
//                            apiSucessResponse.apiStatus("0",message);
//                        }
//
//                        //   Toast.makeText(getApplicationContext(), obj.getString("message"), Toast.LENGTH_SHORT).show();
//                    } catch (JSONException e) {
//                        e.printStackTrace();
//                    }
//                }
//            }, new Response.ErrorListener() {
//                @Override
//                public void onErrorResponse(VolleyError error) {
//                    // Toast.makeText(getApplicationContext(), error.getMessage(), Toast.LENGTH_LONG).show();
//                  //  Log.e("GotError", "" + error.getMessage());
//                    pDialog.dismiss();
//                    // multipartrequest(name, filesize, Outfilename);
//                }
//            }) {
//
//
//                @Override
//                protected Map<String, DataPart> getByteData() {
//                    Map<String, DataPart> params = new HashMap<>();
//                    String imaname = ".jpeg";
//                    String pfline = ".pdf";
//                    if(adharstatus.equals("2")){
//                        params.put("aadhar", new DataPart(pfline, adharDoc));
//                    }else {
//                        params.put("aadhar", new DataPart(imaname, getFileDataFromDrawable(aadharimg)));
//                    }
//                    if(panstatus.equals("2")){
//                        params.put("pan_card", new DataPart(pfline, panDoc));
//                    }else {
//                        params.put("pan_card", new DataPart(imaname, getFileDataFromDrawable(panimg)));
//                    }
//
//
//                    return params;
//                }
//
//                @Override
//                protected Map<String, String> getParams() throws AuthFailureError {
//                    Map<String, String> params = new HashMap<>();
////                    params.put("authToken", authcode);
//                    params.put("first_name", fname);
//                    params.put("last_name", lname);
//                    params.put("email", email);
//                    params.put("phone", mob);
//                    params.put("company_name", compnyname);
//                    params.put("gst_number", gstNo);
//                    params.put("password", pass1);
//                    params.put("confirm_password", pass2);
//                    params.put("deviceId", deviceid);
//                    params.put("deviceType", device_type);
//                    params.put("ip", ipaddress);
//
//                    params.put("gender", gender);
//                    params.put("dob", dob);
//                    params.put("terms_check", "1");
//                 //   Log.e("signupresponse", ","+fname + "," + lname + "," + email + "," + mob + "," + compnyname + "," + gstNo + "," + pass1 + "," + pass2 + "," + deviceid + "," + device_type + "," + ipaddress+","+gender+","+dob);
//
//
//                    return params;
//
//
//                }
//            };
//            volleyMultipartRequest.setRetryPolicy(new DefaultRetryPolicy(
//                    0,
//                    DefaultRetryPolicy.DEFAULT_MAX_RETRIES,
//                    DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));
//            //adding the request to volley
//            Volley.newRequestQueue(context).add(volleyMultipartRequest);
//        } catch (Exception e) {
//            e.printStackTrace();
//        }


        try {
            Log.e("sadsadsaa", "eeee");
            this.carteResponse = apiSucessResponse;
            String regdetails = ServerURL.userregsiration();
            HashMap<String, String> hashMap = new HashMap<>();
            hashMap.put("first_name", fname);
            hashMap.put("last_name", lname);
            hashMap.put("email", email);
            hashMap.put("phone", mob);
            hashMap.put("company_name", compnyname);
            hashMap.put("gst_number", gstNo);
            hashMap.put("password", pass1);
            hashMap.put("confirm_password", pass2);
            hashMap.put("deviceId", deviceid);
            hashMap.put("deviceType", device_type);
            hashMap.put("ip", ipaddress);
            hashMap.put("gender", gender);
            hashMap.put("dob", dob);
            hashMap.put("terms_check", "1");
            Log.e("signupresponse", regdetails+","+fname + "," + lname + "," + email + "," + mob + "," + compnyname + "," + gstNo + "," + pass1 + "," + pass2 + "," + deviceid + "," + device_type + "," + ipaddress+","+gender+","+dob);
            NetworkServiceCall serviceCall = new NetworkServiceCall(context, true);
            serviceCall.setOnServiceCallCompleteListener(new registerCallCompleteListener());
            serviceCall.makeJSONObjectPostStringRequest(regdetails, hashMap, Request.Priority.HIGH);

        } catch (Exception e) {
            e.printStackTrace();
        }


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

    public void OtpApicall(String phonepin, String authdcode, ApiSucessResponse apiSucessResponse) {
        try {
            this.apiSucessResponse = apiSucessResponse;
            String regdetails = ServerURL.verifyOtp();
            HashMap<String, String> hashMap = new HashMap<>();
            hashMap.put("otp", phonepin);
            hashMap.put("authToken", authdcode);
            Log.e("otpresponse", phonepin + "," + authdcode);
            NetworkServiceCall serviceCall = new NetworkServiceCall(context, true);
            serviceCall.setOnServiceCallCompleteListener(new VerifyotpneServiceCallCompleteListener());
            serviceCall.makeJSONObjectPostStringRequest(regdetails, hashMap, Request.Priority.HIGH);

        } catch (Exception e) {
            e.printStackTrace();
        }
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
                    com.cilpl.Cynergytx_admin.signage.update.AppUpdateManager
                            .installedVersionName(context));
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

    public void GetListDetails() {
        try {
            String confirmpass = ServerURL.GetProfile();
            HashMap<String, String> hashMap = new HashMap<>();
            hashMap.put("authToken", authcode);
            //  Log.e("getprofile", confirmpass + "," + authcode);
            NetworkServiceCall serviceCall = new NetworkServiceCall(context, true);
            serviceCall.setOnServiceCallCompleteListener(new gtprofileCallCompleteListener());
            serviceCall.makeJSONObjectPostStringRequest(confirmpass, hashMap, Request.Priority.HIGH);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void ForgetPasswordEmail(String email, ApiSucessResponse apiSucessResponse) {
        try {
            this.apiSucessResponse = apiSucessResponse;
            String forgetdetails = ServerURL.ForgetPassword();
            HashMap<String, String> hashMap = new HashMap<>();
            hashMap.put("user_name", email);
          //  Log.e("forgetpassword", forgetdetails + "," + email);
            NetworkServiceCall serviceCall = new NetworkServiceCall(context, true);
            serviceCall.setOnServiceCallCompleteListener(new ForgetpasswordviceCallCompleteListener());
            serviceCall.makeJSONObjectPostStringRequest(forgetdetails, hashMap, Request.Priority.HIGH);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void newPasswordCreation(String authtoken, String pass1, String pass2, ApiSucessResponse apiSucessResponse) {
        try {
            this.apiSucessResponse = apiSucessResponse;
            String confirmpass = ServerURL.CreatePassord();
            HashMap<String, String> hashMap = new HashMap<>();
            hashMap.put("authToken", authtoken);
            hashMap.put("password", pass1);
            hashMap.put("confirm_password", pass2);
          //  Log.e("otpresponse", confirmpass + "," + authtoken + "," + pass1 + "," + pass2);
            NetworkServiceCall serviceCall = new NetworkServiceCall(context, true);
            serviceCall.setOnServiceCallCompleteListener(new ConfirmviceCallCompleteListener());
            serviceCall.makeJSONObjectPostStringRequest(confirmpass, hashMap, Request.Priority.HIGH);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void UpdateProfile(String authtoken, String f_name, String l_name, String email, String phone, String gender, String dataofbirth, String com_name, String gstno, String city,
                              String state, ApiSucessResponse apiSucessResponse,
                              Bitmap aadharimg, Bitmap panimg, String adharstatus, String panstatus, String adhardoc, String pandoc) {

          Log.e("Notificationststus", "status Success" + "," + userdata.getString("item", ""));

         Log.e("iiiiiiiiiii", panstatus+","+adharstatus);
        try {
            pDialog = new ProgressDialog(context);
            pDialog.setMessage("Uploading...");
            pDialog.setCancelable(false);
            pDialog.getWindow().setBackgroundDrawable(new ColorDrawable(android.graphics.Color.TRANSPARENT));
            pDialog.setIndeterminate(true);
            pDialog.setCanceledOnTouchOutside(false);
            pDialog.show();
            pDialog.setContentView(R.layout.my_network_progress);



            if(adharstatus.equals("2")){
                uri = Uri.fromFile(new File((adhardoc)));
                iStream = context.getContentResolver().openInputStream(uri);
                assert iStream != null;
                adharDoc = getBytes(iStream);

            }
            if(panstatus.equals("2")){
                uri = Uri.fromFile(new File((pandoc)));
                iStream = context.getContentResolver().openInputStream(uri);
                assert iStream != null;
                panDoc = getBytes(iStream);

            }

            VolleyMultipartRequest volleyMultipartRequest = new VolleyMultipartRequest(Request.Method.POST,
                    ServerURL.UpdateProfile(), new Response.Listener<NetworkResponse>() {
                @Override
                public void onResponse(NetworkResponse response) {
                    try {
                        JSONObject obj = new JSONObject(new String(response.data));
                        Log.e("profileuploadadetails", obj.toString());
                        String status = obj.getString("status");
                        String errorCode = obj.getString("errorCode");
                        String message = obj.getString("message");
                        pDialog.dismiss();
                       // Log.e("changeimage", "" + status + "," + errorCode + "," + message);
                        if (status.equals("1")) {
                            Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
                            apiSucessResponse.apiStatus("1");
                        } else {
                            apiSucessResponse.apiStatus("0");
                        }

                        //   Toast.makeText(getApplicationContext(), obj.getString("message"), Toast.LENGTH_SHORT).show();
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            }, new Response.ErrorListener() {
                @Override
                public void onErrorResponse(VolleyError error) {
                    // Toast.makeText(getApplicationContext(), error.getMessage(), Toast.LENGTH_LONG).show();
                  //  Log.e("GotError", "" + error.getMessage());
                    pDialog.dismiss();
                    // multipartrequest(name, filesize, Outfilename);
                }
            }) {


                @Override
                protected Map<String, DataPart> getByteData() {
                    Map<String, DataPart> params = new HashMap<>();
                    String imaname = ".jpeg";
                    String pfline = ".pdf";
                    if(adharstatus.equals("2")){
                        params.put("aadhar", new DataPart(pfline, adharDoc));
                    }else {
                        params.put("aadhar", new DataPart(imaname, getFileDataFromDrawable(aadharimg)));
                    }
                    if(panstatus.equals("2")){
                        params.put("pan_card", new DataPart(pfline, panDoc));
                    }else {
                        params.put("pan_card", new DataPart(imaname, getFileDataFromDrawable(panimg)));
                    }


                    return params;
                }

                @Override
                protected Map<String, String> getParams() throws AuthFailureError {
                    Map<String, String> hashMap = new HashMap<>();
//                    params.put("authToken", authcode);

                    hashMap.put("authToken", authtoken);
                    hashMap.put("first_name", f_name);
                    hashMap.put("last_name", l_name);
                    hashMap.put("email", email);
                    hashMap.put("phone", phone);
                    hashMap.put("gender", gender);
                    hashMap.put("dob", dataofbirth);
                    hashMap.put("company_name", com_name);
                    hashMap.put("gst_number", gstno);
                    hashMap.put("city", city);
                    hashMap.put("state", state);
                 //   Log.e("signupresponse", ","+fname + "," + lname + "," + email + "," + mob + "," + compnyname + "," + gstNo + "," + pass1 + "," + pass2 + "," + deviceid + "," + device_type + "," + ipaddress+","+gender+","+dob);


                    return hashMap;


                }
            };
            volleyMultipartRequest.setRetryPolicy(new DefaultRetryPolicy(
                    0,
                    DefaultRetryPolicy.DEFAULT_MAX_RETRIES,
                    DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));
            //adding the request to the app-wide singleton queue (never create a new queue per
            // request — that leaks ~5 threads each time and eventually crashes with pthread OOM)
            com.cilpl.Cynergytx_admin.Activites.MySingleton.getInstance(context).addToRequestQueue(volleyMultipartRequest);
        } catch (Exception e) {
            e.printStackTrace();
        }










     // normal code without kyc

//        try {
//            this.apiSucessResponse = apiSucessResponse;
//            String confirmpass = ServerURL.UpdateProfile();
//            HashMap<String, String> hashMap = new HashMap<>();
//            hashMap.put("authToken", authtoken);
//            hashMap.put("first_name", f_name);
//            hashMap.put("last_name", l_name);
//            hashMap.put("email", email);
//            hashMap.put("phone", phone);
//            hashMap.put("gender", gender);
//            hashMap.put("dob", dataofbirth);
//            hashMap.put("company_name", com_name);
//            hashMap.put("gst_number", gstno);
//            hashMap.put("city", city);
//            hashMap.put("state", state);
//          //  Log.e("profileUpdate", f_name + "," + l_name + "," + email + "," + gender + "," + dataofbirth + "," + com_name + "," + gstno + "," + city + "," + state + ",");
//            NetworkServiceCall serviceCall = new NetworkServiceCall(context, true);
//            serviceCall.setOnServiceCallCompleteListener(new UpdateprofileserviceCallCompleteListener());
//            serviceCall.makeJSONObjectPostStringRequest(confirmpass, hashMap, Request.Priority.HIGH);
//            // delete total files in folder
//            //dletefiles();
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
    }

    public void AddCart(String authtoken, String bookintype, String bookid, String sdate, String edate, String chairlist, String seates, String booktitle, CarteResponse carteResponse) {
        try {
            this.cuteResponse = carteResponse;
            String confirmpass = ServerURL.addCart();
            HashMap<String, String> hashMap = new HashMap<>();
            hashMap.put("authToken", authtoken);
            hashMap.put("booking_type", bookintype);

            hashMap.put("product_id", bookid);
            hashMap.put("from", sdate);
            hashMap.put("to", edate);
            hashMap.put("chairlist", chairlist);

            hashMap.put("capacity", seates);
            hashMap.put("product_name", booktitle);
            NetworkServiceCall serviceCall = new NetworkServiceCall(context, true);
            serviceCall.setOnServiceCallCompleteListener(new AddcartslotsviceCallCompleteListener(carteResponse));
            Log.e("AddCart", confirmpass + "," + authtoken + "," +bookid+","+ sdate + "," + edate + "," + seates + "," + chairlist);
            serviceCall.makeJSONObjectPostStringRequest(confirmpass, hashMap, Request.Priority.HIGH);
            // delete total files in folder
            //dletefiles();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    public void Bookingslotdates(String authtoken, String bookid, String sdate, String edate, String seates, String btype, ApiSucessResponse apiSucessResponse) {
        try {
            this.apiSucessResponse = apiSucessResponse;
            String confirmpass = ServerURL.CheckAvailability();
            HashMap<String, String> hashMap = new HashMap<>();
            hashMap.put("authToken", authtoken);
            hashMap.put("id", bookid);
            hashMap.put("from", sdate);
            hashMap.put("to", edate);
            hashMap.put("number_of", seates);
            hashMap.put("booking_type", btype);
            NetworkServiceCall serviceCall = new NetworkServiceCall(context, true);
            serviceCall.setOnServiceCallCompleteListener(new BookingslotsviceCallCompleteListener());
           // Log.e("bookingslot", confirmpass + "," + authtoken + "," + bookid + "," + sdate + "," + edate + "," + seates + "," + btype);
            serviceCall.makeJSONObjectPostStringRequest(confirmpass, hashMap, Request.Priority.HIGH);
            // delete total files in folder
            //dletefiles();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void BookingConfirmdetails(String authtoken, String bookid, String sdate, String edate, String seates, String booktype, String uname, String emailid, String phonenum, String companyName, String gstNo, ApiSucessResponse apiSucessResponse) {
        try {
            this.apiSucessResponse = apiSucessResponse;
            String confirmpass = ServerURL.BookNow();
            HashMap<String, String> hashMap = new HashMap<>();
            hashMap.put("authToken", authtoken);
            hashMap.put("product_id", bookid);
            hashMap.put("from", sdate);
            hashMap.put("to", edate);
            hashMap.put("number_of", seates);
            hashMap.put("booking_type", booktype);
            hashMap.put("name", uname);
            hashMap.put("email", emailid);
            hashMap.put("phone", phonenum);
            hashMap.put("company_name", companyName);
            hashMap.put("gst_number", gstNo);
           // Log.e("bookingslot", confirmpass + "," + authtoken + "," + bookid + "," + booktype + "," + uname + "," + emailid + "," + phonenum + "," + companyName + "," + gstNo);
            NetworkServiceCall serviceCall = new NetworkServiceCall(context, true);
            serviceCall.setOnServiceCallCompleteListener(new ConfirmdetailssviceCallCompleteListener());
            serviceCall.makeJSONObjectPostStringRequest(confirmpass, hashMap, Request.Priority.HIGH);
            // delete total files in folder
            //dletefiles();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void Supportdetails(String uname, String email, String phone, String supportoption, String message, ApiSucessResponse apiSucessResponse) {
        try {
            this.apiSucessResponse = apiSucessResponse;
            String addsupport = ServerURL.AddSupport();
            HashMap<String, String> hashMap = new HashMap<>();
            hashMap.put("full_name", uname);
            hashMap.put("email", email);
            hashMap.put("phone", phone);
            hashMap.put("support_for", supportoption);
            hashMap.put("message", message);

            NetworkServiceCall serviceCall = new NetworkServiceCall(context, true);
            serviceCall.setOnServiceCallCompleteListener(new SupportsviceCallCompleteListener());
         //   Log.e("supportPage", addsupport + "," + uname + "," + email + "," + phone + "," + supportoption + "," + message);
            serviceCall.makeJSONObjectPostStringRequest(addsupport, hashMap, Request.Priority.HIGH);

        } catch (Exception e) {
            e.printStackTrace();
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


    public void ServiceRequest(String authtoken, String name, String email, String phonenum, String compynyname, String n_business, String reg_plan, String city, ApiSucessResponse apiSucessResponse) {
        try {
            this.apiSucessResponse = apiSucessResponse;
            String serviceRequest = ServerURL.serviceRequest();
            HashMap<String, String> hashMap = new HashMap<>();
            hashMap.put("authToken", authtoken);
            hashMap.put("full_name", name);
            hashMap.put("email", email);
            hashMap.put("phone", phonenum);
            hashMap.put("c_name", compynyname);
            hashMap.put("b_type", n_business);
            hashMap.put("plan_type", reg_plan);
            hashMap.put("city", city);
            Log.e("ServiceRequest", serviceRequest + "," + authtoken + "," + name + "," + email + "," + phonenum + "," + compynyname + "," + n_business + "," + reg_plan + "," + city);
            NetworkServiceCall serviceCall = new NetworkServiceCall(context, true);
            serviceCall.setOnServiceCallCompleteListener(new ServiceRequestssviceCallCompleteListener());
            serviceCall.makeJSONObjectPostStringRequest(serviceRequest, hashMap, Request.Priority.HIGH);

        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    public void SendSlotData(String startdate, String timeslots, int timeslotssize, String status, String chairslots, int chairslotssize, String s) {
        try {
            String senddata = ServerURL.GetSlotData();
            HashMap<String, String> hashMap = new HashMap<>();
            hashMap.put("authToken", authcode);
            final JSONObject mainobject = new JSONObject();
            final JSONArray array = new JSONArray();
            final JSONObject object1 = new JSONObject();


//                   final JSONObject object2 = new JSONObject();
            object1.put("date", startdate);
            object1.put("timeslot", timeslots);
            object1.put("slotcount", timeslotssize);
            object1.put("chairlist", chairslots);

            array.put(object1);


            mainobject.put("Data", array);
         //   Log.e("printlist", array.toString());
            hashMap.put("data", array.toString());
            if (!s.equals("0")) {
                hashMap.put("recordId", s);
            }
           // Log.e("PracticeAcitivty", senddata + "," + authcode + "," + array.toString() + "," + s);
            NetworkServiceCall serviceCall = new NetworkServiceCall(context, true);
            serviceCall.setOnServiceCallCompleteListener(new endBookdataCompleteListener(startdate, timeslots, timeslotssize, status, chairslots, chairslotssize));
            serviceCall.makeJSONObjectPostStringRequest(senddata, hashMap, Request.Priority.HIGH);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }




    public void Addenquery(String authtoken, String uname, String mobilenum, String orgname,
                           String cmpyrelated, String emailId, String lookFor, String plandate,
                           String remarks, String extrainc, String address, CarteResponse apiSucessResponse) {
        try {
            this.carteResponse = apiSucessResponse;
            String serviceRequest = ServerURL.Add_enquiry();
            HashMap<String, String> hashMap = new HashMap<>();
            hashMap.put("authToken", authtoken);
            hashMap.put("name", uname);
            hashMap.put("phone", mobilenum);
            hashMap.put("org_name", orgname);
            hashMap.put("company_related", cmpyrelated);
            hashMap.put("email", emailId);
            hashMap.put("looking_for", lookFor);
            hashMap.put("enquiry_date", plandate);
            hashMap.put("remarks", remarks);
            hashMap.put("requirement", extrainc);
            hashMap.put("address", address);
            NetworkServiceCall serviceCall = new NetworkServiceCall(context, true);
            serviceCall.setOnServiceCallCompleteListener(new AddenqRequestssviceCallCompleteListener());
            serviceCall.makeJSONObjectPostStringRequest(serviceRequest, hashMap, Request.Priority.HIGH);

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

                com.cilpl.Cynergytx_admin.signage.update.AppUpdateManager.check(
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

    private class registerCallCompleteListener implements OnServiceCallCompleteListener {
        @Override
        public void onJSONObjectResponse(JSONObject jsonObject) {
            try {
                Log.e("signupresponse", jsonObject.toString());
                String status = jsonObject.getString("status");
                String errorcode = jsonObject.getString("errorCode");
                String message = jsonObject.getString("message");
                if (status.equals("1")) {
                    String otp = jsonObject.getString("OTP");
                    String authtokenmessage = jsonObject.getString("authToken");
                    String uid = jsonObject.getString("id");
                    SharedPreferences.Editor editor = userdata.edit();
                    editor.putString("authToken", authtokenmessage);
                    editor.putString("userId", uid);
                    editor.putString("otp", otp);
                    editor.apply();
                    carteResponse.apiStatus("1",message);
                } else {
                    carteResponse.apiStatus("0",message);
                }


            } catch (Exception e) {
                e.printStackTrace();
            }
           // Log.e("firebaseupload", "uploaded");
        }

        @Override
        public void onJSONArrayResponse(JSONArray jsonArray) {

        }

        @Override
        public void onErrorResponse(VolleyError error) {

        }
    }

    private class verifyotpneServiceCallCompleteListener implements OnServiceCallCompleteListener {
        @Override
        public void onJSONObjectResponse(JSONObject jsonObject) {

        }

        @Override
        public void onJSONArrayResponse(JSONArray jsonArray) {

        }

        @Override
        public void onErrorResponse(VolleyError error) {

        }
    }


    private class VerifyotpneServiceCallCompleteListener implements OnServiceCallCompleteListener {
        @Override
        public void onJSONObjectResponse(JSONObject jsonObject) {
           // Log.e("otpresponse", jsonObject.toString());

            try {
             //   Log.e("signupresponse", jsonObject.toString());
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


            Toast.makeText(context, "Registered Sucessfully.", Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onJSONArrayResponse(JSONArray jsonArray) {

        }

        @Override
        public void onErrorResponse(VolleyError error) {

        }
    }

    private class ForgetpasswordviceCallCompleteListener implements OnServiceCallCompleteListener {
        @Override
        public void onJSONObjectResponse(JSONObject jsonObject) {
            try {
                //Log.e("forgetpassword", jsonObject.toString());
                String status = jsonObject.getString("status");
                String errorcode = jsonObject.getString("errorCode");
                String message = jsonObject.getString("message");
                if (status.equals("1")) {
                    String otp = jsonObject.getString("OTP");
                    String authtokenmessage = jsonObject.getString("authToken");
                    SharedPreferences.Editor editor = userdata.edit();
                    editor.putString("authToken", authtokenmessage);
                    editor.putString("otp", otp);
                    editor.apply();
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

    private class ConfirmviceCallCompleteListener implements OnServiceCallCompleteListener {
        @Override
        public void onJSONObjectResponse(JSONObject jsonObject) {
            try {
               // Log.e("signupresponse", jsonObject.toString());
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

    private class gtprofileCallCompleteListener implements OnServiceCallCompleteListener {
        @Override
        public void onJSONObjectResponse(JSONObject jsonObject) {
            try {
               // Log.e("getprofile", jsonObject.toString());
                String status = jsonObject.getString("status");
                String errorcode = jsonObject.getString("errorCode");
                String message = jsonObject.getString("message");
                String imagePath = jsonObject.getString("imagePath");
                if (status.equals("1")) {
                    JSONObject object = jsonObject.getJSONObject("data");
                    String f_name = object.getString("first_name");
                    String l_name = object.getString("last_name");
                    String email = object.getString("email");
                    String phone = object.getString("phone");
                    String com_name = object.getString("company_name");
                    String gstno = object.getString("gst_number");
                    String city = object.getString("city");
                    String country = object.getString("country");
                    String state = object.getString("state");
                    String pincode = object.getString("pin_code");
                    String gender = object.getString("gender");
                    String dataofbirth = object.getString("dob");
                    String photo = object.getString("photo");
                    SharedPreferences.Editor editor = userdata.edit();
                    editor.putString("firstname", f_name);
                    editor.putString("email", email);
                    editor.putString("phne", phone);
                    editor.putString("compy_name", com_name);
                    editor.putString("gst_no", gstno);
                    editor.putString("profileimage", imagePath + photo);
                    editor.apply();


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

    private class UpdateprofileserviceCallCompleteListener implements OnServiceCallCompleteListener {
        @Override
        public void onJSONObjectResponse(JSONObject jsonObject) {
            try {
               // Log.e("profileUpdate", jsonObject.toString());
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

    private class BookingslotsviceCallCompleteListener implements OnServiceCallCompleteListener {
        @Override
        public void onJSONObjectResponse(JSONObject jsonObject) {
            try {
              //  Log.e("bookingslot", jsonObject.toString());
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

    private class ConfirmdetailssviceCallCompleteListener implements OnServiceCallCompleteListener {
        @Override
        public void onJSONObjectResponse(JSONObject jsonObject) {
            try {
              //  Log.e("bookingslot", jsonObject.toString());
                String status = jsonObject.getString("status");
                String errorcode = jsonObject.getString("errorCode");
                String message = jsonObject.getString("message");
                String data = jsonObject.getString("Data");
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

    private class AddcartslotsviceCallCompleteListener implements OnServiceCallCompleteListener {
        CarteResponse carteResponse1;

        public AddcartslotsviceCallCompleteListener(CarteResponse carteResponse) {
            this.carteResponse1 = carteResponse;
        }

        @Override
        public void onJSONObjectResponse(JSONObject jsonObject) {
            try {
                Log.e("AddCart", jsonObject.toString());
                String status = jsonObject.getString("status");
                String errorcode = jsonObject.getString("errorCode");
                String message = jsonObject.getString("message");
                String data = jsonObject.getString("Data");
                String count = jsonObject.getString("Count");
                if (status.equals("1")) {
                    carteResponse1.apiStatus("1", count);
                    cuteResponse.apiStatus("1", count);
                } else {
                    carteResponse1.apiStatus("0", count);
                    cuteResponse.apiStatus("0", count);
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

    private class SupportsviceCallCompleteListener implements OnServiceCallCompleteListener {
        @Override
        public void onJSONObjectResponse(JSONObject jsonObject) {
            try {
               // Log.e("supportPage", jsonObject.toString());
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

    private class ServiceRequestssviceCallCompleteListener implements OnServiceCallCompleteListener {
        @Override
        public void onJSONObjectResponse(JSONObject jsonObject) {
            try {

                Log.e("ServiceRequest", jsonObject.toString());
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

    private class endBookdataCompleteListener implements OnServiceCallCompleteListener {
        private String date1, timeslots, chairslots, status;
        int timesloysize, chairslotcount;

        public endBookdataCompleteListener(String startdate, String timeslots, int timeslotssize, String status, String chairslots, int chairslotssize) {
            this.date1 = startdate;
            this.timeslots = timeslots;
            this.chairslots = chairslots;
            this.status = status;
            this.chairslotcount = chairslotssize;
            this.timesloysize = timeslotssize;
        }

        @Override
        public void onJSONObjectResponse(JSONObject jsonObject) {
            try {
           //     Log.e("PracticeAcitivty", jsonObject.toString());
                String status = jsonObject.getString("status");
                String errorcode = jsonObject.getString("errorCode");
                String message = jsonObject.getString("message");
                String recordid = jsonObject.getString("recordId");

                if (status.equals("1")) {
                    InsertInotDB(date1, timeslots, timesloysize, status, chairslots, chairslotcount, recordid);

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

    private class SubmitData implements OnServiceCallCompleteListener {
        @Override
        public void onJSONObjectResponse(JSONObject jsonObject) {
            try {
            //    Log.e("submittotallist", jsonObject.toString());
                String status = jsonObject.getString("status");
                String errorcode = jsonObject.getString("errorCode");
                String message = jsonObject.getString("message");
                String count = jsonObject.getString("Count");
                if (status.equals("1")) {
                    carteResponse.apiStatus("1",count);
                    SqlLiteDb db = new SqlLiteDb(context);
                    db.deleteAll();
                    database.close();
                } else {
                    carteResponse.apiStatus("0",count);
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






    private class AddenqRequestssviceCallCompleteListener implements OnServiceCallCompleteListener {
        @Override
        public void onJSONObjectResponse(JSONObject jsonObject) {
            try {
                    Log.e("AddEnquiry", jsonObject.toString());
                String status = jsonObject.getString("status");
                String errorcode = jsonObject.getString("errorCode");
                String message = jsonObject.getString("message");
                if (status.equals("1")) {
                    carteResponse.apiStatus("1",message);

                } else {
                    carteResponse.apiStatus("0",message);
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
                String scheduleJson = com.cilpl.Cynergytx_admin.signage.model.ContentSchedule
                        .preservePptSlides(scheduleArray, userdata.getString("schedule_json", ""));
                editor1.putString("schedule_json", scheduleJson);

                // The admin panel advertises the current build here. Returns immediately; the
                // download runs in the background and never holds up the schedule.
                com.cilpl.Cynergytx_admin.signage.update.AppUpdateManager.check(
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




