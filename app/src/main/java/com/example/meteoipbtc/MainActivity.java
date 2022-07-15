package com.example.meteoipbtc;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.ResolvableApiException;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResponse;
import com.google.android.gms.location.LocationSettingsStatusCodes;
import com.google.android.gms.tasks.Task;

import org.eclipse.paho.android.service.MqttAndroidClient;
import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigInteger;
import java.net.URL;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.net.ssl.HttpsURLConnection;


public class MainActivity extends AppCompatActivity {
    // Début variables GPS/BTC/Météo

    TextView geoTextView;
    TextView tempTextView;
    TextView btcTextView;
    TextView gpsTextView;

    // LOCALISATION VIA GPS
    LocationRequest locationRequest;
    String latitude;
    String longitude;

    // Fin Variables GPS



    // Début MQTT

    private final String TAG = "AiotMqtt";
    /* Informations triples sur l'appareil */
    final private String PRODUCTKEY = "a11xsrWmW14";
    final private String DEVICENAME = "paho_android";

    /* Sujet automatique, pour recevoir des messages */
    final private String SUB_TOPIC = "/" + PRODUCTKEY + "/" + DEVICENAME + "/user/get";

    /* Nom de domaine du serveur Mqtt HiveMQ */
    final String host = "tcp://" + PRODUCTKEY + "broker.hivemq.com:1883";
    private String clientId;
    private String userName;
    private String passWord;

    MqttAndroidClient mqttAndroidClient;


    // Fin MQTT


    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        // Références des textviews
        geoTextView = findViewById(R.id.geo);
        tempTextView = findViewById(R.id.temp);
        btcTextView = findViewById(R.id.btc);
        Button search = findViewById(R.id.search);
        Button reset = findViewById(R.id.reset);
        gpsTextView = findViewById(R.id.gps);

        locationRequest = LocationRequest.create();
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        locationRequest.setInterval(5000);
        locationRequest.setFastestInterval(2000);
        turnOnGPS();
        getCurrentLocation();

        // Réagir au click sur le bouton SEARCH :
        search.setOnClickListener(v -> new MeteoTask().execute());

        // Réagir au click sur le bouton RESET :
        reset.setOnClickListener(v -> {
            geoTextView.setText("");
            tempTextView.setText("");
            btcTextView.setText("");
            gpsTextView.setText("");
        });

        setContentView(R.layout.activity_main);

        String DEVICESECRET = "tLMT9QWD36U2SArglGqcHCDK9rK9nOrA";
        AiotMqttOption aiotMqttOption = new AiotMqttOption().getMqttOption(PRODUCTKEY, DEVICENAME, DEVICESECRET);
        if (aiotMqttOption == null) {
            Log.e(TAG, "device info error");
        } else {
            clientId = aiotMqttOption.getClientId();
            userName = aiotMqttOption.getUsername();
            passWord = aiotMqttOption.getPassword();
        }

        MqttConnectOptions mqttConnectOptions = new MqttConnectOptions();
        mqttConnectOptions.setUserName(userName);
        mqttConnectOptions.setPassword(passWord.toCharArray());

        mqttAndroidClient = new MqttAndroidClient(getApplicationContext(), host, clientId);
        mqttAndroidClient.setCallback(new MqttCallback() {
            @Override
            public void connectionLost(Throwable cause) {
                Log.i(TAG, "connection lost");
            }

            @Override
            public void messageArrived(String topic, MqttMessage message) {
                Log.i(TAG, "topic: " + topic + ", msg: " + new String(message.getPayload()));
            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken token) {
                Log.i(TAG, "msg delivered");
            }
        });

        // Tentative de connexion au broker MQTT
        try {
            mqttAndroidClient.connect(mqttConnectOptions,null, new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    Log.i(TAG, "connect succeed");

                    subscribeTopic(SUB_TOPIC);
                }

                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                    Log.i(TAG, "connect failed");
                }
            });

        } catch (MqttException e) {
            e.printStackTrace();
        }

        Button pubButton = findViewById(R.id.publish);
        pubButton.setOnClickListener(view -> publishMessage("hello IoT"));
    }

    // Souscription aux topic

    public void subscribeTopic(String topic) {
        try {
            mqttAndroidClient.subscribe(topic, 0, null, new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    Log.i(TAG, "subscribed succeed");
                }

                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                    Log.i(TAG, "subscribed failed");
                }
            });

        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    public void publishMessage(String payload) {
        try {
            if (!mqttAndroidClient.isConnected()) {
                mqttAndroidClient.connect();
            }

            MqttMessage message = new MqttMessage();
            message.setPayload(payload.getBytes());
            message.setQos(0);
            /* Sujet automatique, pour publier des messages */
            String PUB_TOPIC = "/" + PRODUCTKEY + "/" + DEVICENAME + "/user/update";
            mqttAndroidClient.publish(PUB_TOPIC, message,null, new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    Log.i(TAG, "publish succeed!");
                }

                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                    Log.i(TAG, "publish failed!");
                }
            });
        } catch (MqttException e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
        }
    }

    /**
     * Classe d'options de connexion MQTT, entrez la clé de produit triple du périphérique,
     * le nom du périphérique et le code secret du périphérique,
     * et générez les paramètres de connexion Mqtt clientId, nom d'utilisateur et mot de passe.
     */
    static class AiotMqttOption {
        private String username = "";
        private String password = "";
        private String clientId = "";

        public String getUsername() { return this.username;}
        public String getPassword() { return this.password;}
        public String getClientId() { return this.clientId;}

        /**
         * Obtenir l'objet d'option de connexion Mqtt
         * @param productKey clé de produit
         * @param deviceName nom de l'appareil
         * @param deviceSecret Confidentialité de l'appareil
         * @return Objet AiotMqttOption ou NULL
         */

        public AiotMqttOption getMqttOption(String productKey, String deviceName, String deviceSecret) {
            if (productKey == null || deviceName == null || deviceSecret == null) {
                return null;
            }

            try {
                String timestamp = Long.toString(System.currentTimeMillis());

                // clientId
                this.clientId = productKey + "." + deviceName + "|timestamp=" + timestamp +
                        ",_v=paho-android-1.0.0,securemode=2,signmethod=hmacsha256|";

                // userName
                this.username = deviceName + "&" + productKey;

                // password
                String macSrc = "clientId" + productKey + "." + deviceName + "deviceName" +
                        deviceName + "productKey" + productKey + "timestamp" + timestamp;
                String algorithm = "HmacSHA256";
                Mac mac = Mac.getInstance(algorithm);
                SecretKeySpec secretKeySpec = new SecretKeySpec(deviceSecret.getBytes(), algorithm);
                mac.init(secretKeySpec);
                byte[] macRes = mac.doFinal(macSrc.getBytes());
                password = String.format("%064x", new BigInteger(1, macRes));
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }

            return this;
        }
    }
    // Fin MQTT


    // Activation du GPS s'il n'est pas actif
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 1){
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED){
                if (isGPSEnabled()) {
                    getCurrentLocation();
                }else {
                    turnOnGPS();
                }
            }
        }
    }


    // Si erreur dans la localisation avec GPS on récupère la localisation par l'IP
    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 2) {
            if (resultCode == Activity.RESULT_OK) {
                // Localisation via GPS si activation du GPS OK
                getCurrentLocation();
            }
        }
    }

    private void getCurrentLocation() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                if (isGPSEnabled()) {
                    LocationServices.getFusedLocationProviderClient(MainActivity.this).requestLocationUpdates(locationRequest, new LocationCallback() {
                        @SuppressLint("SetTextI18n")
                        @Override
                        public void onLocationResult(@NonNull LocationResult locationResult) {
                            super.onLocationResult(locationResult);

                            LocationServices.getFusedLocationProviderClient(MainActivity.this).removeLocationUpdates(this);

                            if (locationResult.getLocations().size() >0){

                                int index = locationResult.getLocations().size() - 1;
                                latitude = String.valueOf(locationResult.getLocations().get(index).getLatitude());
                                longitude = String.valueOf(locationResult.getLocations().get(index).getLongitude());

                            }
                        }
                    }, Looper.getMainLooper());
                } else {
                    turnOnGPS();
                }
            } else {
                requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);
            }
        }
    }

    private void turnOnGPS() {

        LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder().addLocationRequest(locationRequest);
        builder.setAlwaysShow(true);
        Task<LocationSettingsResponse> result = LocationServices.getSettingsClient(getApplicationContext()).checkLocationSettings(builder.build());
        result.addOnCompleteListener(task -> {
            try {
                // LocationSettingsResponse response = task.getResult(ApiException.class);
                task.getResult(ApiException.class);
                Toast.makeText(MainActivity.this, "Le GPS est déjà activé", Toast.LENGTH_SHORT).show();
            } catch (ApiException e) {
                switch (e.getStatusCode()) {
                    case LocationSettingsStatusCodes.RESOLUTION_REQUIRED:
                        try {
                            ResolvableApiException resolvableApiException = (ResolvableApiException) e;
                            resolvableApiException.startResolutionForResult(MainActivity.this, 2);
                        } catch (IntentSender.SendIntentException ex) {
                            ex.printStackTrace();
                        }
                        break;
                    case LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE:
                        //Device does not have location
                        break;
                }
            }
        });

    }

    private boolean isGPSEnabled() {
        LocationManager locationManager;
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
    }

    @SuppressLint("StaticFieldLeak")
    private class MeteoTask extends AsyncTask<String, Void, String[]> {

        public String callHttpsAPI(String urlString) throws IOException, JSONException {
            URL url = new URL(urlString);
            HttpsURLConnection urlConnection = (HttpsURLConnection) url.openConnection();

            //Paramètres de la requête HTTP
            urlConnection.setRequestMethod("GET");
            urlConnection.setReadTimeout(1000); // en ms
            urlConnection.setConnectTimeout(1000); // en ms
            urlConnection.setDoOutput(true);

            //Lancement de la requête HTTP
            urlConnection.connect();

            BufferedReader br = new BufferedReader(new InputStreamReader(url.openStream()));
            StringBuilder sb = new StringBuilder();

            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line).append("\n");
            }
            br.close();

            String jsonString = sb.toString();
            System.out.println("JSON: " + jsonString);

            return jsonString;
        }

        // Passage de chiffre à lettre pour le mois
        public String mois(String moisT) {

            String mois = "";
            switch (moisT) {
                case "01":
                    mois = "janvier";
                    break;
                case "02":
                    mois = "février";
                    break;
                case "03":
                    mois = "mars";
                    break;
                case "04":
                    mois = "avril";
                    break;
                case "05":
                    mois = "mai";
                    break;
                case "06":
                    mois = "juin";
                    break;
                case "07":
                    mois = "juillet";
                    break;
                case "08":
                    mois = "août";
                    break;
                case "09":
                    mois = "septembre";
                    break;
                case "10":
                    mois = "octobre";
                    break;
                case "11":
                    mois = "novembre";
                    break;
                case "12":
                    mois = "decembre";
                    break;
            }
            return mois;
        }

        protected void onPreExecute () {
            super.onPreExecute();
        }
        String typeOfLoc;
        @SuppressLint("MissingPermission")
        @Override
        protected String[] doInBackground (String...args){
            String[] result = new String[3];

            String btcString = "https://api.coindesk.com/v1/bpi/currentprice.json";
            String metString;

            try {


                ///////////////////////// UNIQUEMENT SI GPS PAS BON
                if ( latitude == null ||  longitude == null ) {
                    //Récupération de l'adresse IP
                    String ip = this.callHttpsAPI("https://api.ipify.org");
                    result[1] = this.callHttpsAPI("https://ipinfo.io/" + ip + "/geo");

                    //Récupération des informations de localisation par l'IP
                    JSONObject resReqWeb = new JSONObject(result[1]);

                    String reqLoc = resReqWeb.getString("loc");
                    String[] longlat = reqLoc.split(",");

                    metString = "https://api.open-meteo.com/v1/forecast?latitude=" + longlat[0] + "&longitude=" + longlat[1] + "&hourly=temperature_2m";
                    typeOfLoc = "(Adresse IP utilisée pour la météo)";
                } else {
                    //On réalise le parsing de "open-meteo" via la requête API qui dépend de la longitude et la latitude de l'utilisateur si la requête GPS n'a pas aboutie
                    metString = "https://api.open-meteo.com/v1/forecast?latitude=" + latitude + "&longitude=" + longitude + "&hourly=temperature_2m";
                    typeOfLoc = "(GPS utilisé pour la météo)";
                }
                result[2] = this.callHttpsAPI(metString);
                result[0] = this.callHttpsAPI(btcString);


            } catch (IOException |JSONException e) {
                e.printStackTrace();
                String[] err = new String[1];
                err[0] = "Exception: " + e.getMessage();
                return err;

            }
            return result;
        }

        // String result c'est le JSON complet
        @SuppressLint("SetTextI18n")
        protected void onPostExecute (String[]result){
            //debut  de la méthode onPostExecute


            try {

                ////////////////////////////////////////////////////////////////////////////

                //Récupération du cours du BTC
                JSONObject resReqWeb = new JSONObject(result[0]);

                //Récupération du court du bitcoin (dans objet EUR qui est dans Objet EUR)
                double biteuro = resReqWeb.getJSONObject("bpi").getJSONObject("EUR").getDouble("rate_float");
                btcTextView.setText("Cours du BTC : " + biteuro + " €");

                ////////////////////////////////////////////////////////////////////////////


                ////////////////////////////////////////////////////////////////////////////

                //Récupération des informations de localisation par l'IP
                resReqWeb = new JSONObject(result[1]);

                //On parse l'objet json récupéré
                String ipA = "L'adresse IP : " + resReqWeb.getString("ip");
                String locA = "A pour localisation : " + resReqWeb.getString("city") + ", " + resReqWeb.getString("postal");

                //Mise a jour du TextView
                geoTextView.setText(ipA + "\n" + locA + "\n" + "\n" + typeOfLoc);

                ////////////////////////////////////////////////////////////////////////////


                ////////////////////////////////////////////////////////////////////////////

                //Récupération des informations de météo (Objet JSON)
                resReqWeb = new JSONObject(result[2]);

                //Récupération de la température
                String tempR = resReqWeb.getJSONObject("hourly").getJSONArray("temperature_2m").getString(13);

                //Récupération de la date et de l'heure
                String dateHeureTemp = resReqWeb.getJSONObject("hourly").getJSONArray("time").getString(13);
                String[] dateHeure = dateHeureTemp.split("T");
                String heure = dateHeure[1];
                String dateB = dateHeure[0];
                String[] date;
                date = dateB.split("-");

                //Formattage textuel
                String time = "À " + heure + " le " + date[2] + " " + mois(date[1]) + " " + date[0];
                String temp = "La témpérature est/sera/était de " + tempR + "°";

                //Mise a jour du TextView
                tempTextView.setText(time + "\n" + temp);

                ////////////////////////////////////////////////////////////////////////////

            } catch (Exception e) {

                e.printStackTrace();
            }

            ////////////////////////////////////////////////////////////////////////////

        } //Fin de la méthode onPostExecute
    }
}