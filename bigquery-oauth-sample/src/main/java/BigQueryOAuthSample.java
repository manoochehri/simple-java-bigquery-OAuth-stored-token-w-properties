import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.auth.oauth2.TokenResponse;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeRequestUrl;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson.JacksonFactory;
import com.google.api.services.bigquery.Bigquery;
import com.google.api.services.bigquery.Bigquery.Datasets;
import com.google.api.services.bigquery.model.DatasetList;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;


class BigQueryOAuthSample {

  // Change this to your current project ID
  private static final String PROJECT_ID = "XXXXXXXXXX";

  // Load Client ID/Secrets from clientsecrets.json file.
  private static final String CLIENTSECRETS_LOCATION = "clientsecrets.json";
  static GoogleClientSecrets clientSecrets = loadClientSecrets();

  private static final String REDIRECT_URI = "urn:ietf:wg:oauth:2.0:oob";
  private static final List<String> SCOPES = Arrays.asList(
      "https://www.googleapis.com/auth/bigquery");
  
  // Objects for handling HTTP transport and JSON formatting of API calls
  private static final HttpTransport TRANSPORT = new NetHttpTransport();
  private static final JsonFactory JSON_FACTORY = new JacksonFactory();

  private static GoogleAuthorizationCodeFlow flow = null;

  // BigQuery Client
  static Bigquery bigqueryclient;

  
  public static void main(String[] args) throws IOException {

    // Attempt to Load existing Refresh Token
    String storedRefreshToken = loadRefreshToken();

    // Check to see if the an existing refresh token was loaded.
    // If so, create a credential and call refreshToken() to get a new
    // access token.
    if (storedRefreshToken != null) {

      // Request a new Access token using the refresh token.
      GoogleCredential credential = createCredentialWithRefreshToken(
          TRANSPORT, JSON_FACTORY, new TokenResponse().setRefreshToken(storedRefreshToken));
      credential.refreshToken();

      bigqueryclient = buildService(credential);

    // If there is no refresh token (or token.properties file), start the OAuth
    // authorization flow.
    } else {
      String authorizeUrl = new GoogleAuthorizationCodeRequestUrl(
          clientSecrets,
          REDIRECT_URI,
          SCOPES).setState("").build();

      System.out.println("Paste this URL into a web browser to authorize BigQuery Access:\n" + authorizeUrl);

      System.out.println("... and type the code you received here: ");
      BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
      String authorizationCode = in.readLine();

      // Exchange the auth code for an access token and refesh token
      Credential credential = exchangeCode(authorizationCode);

      // Store the refresh token for future use.
      storeRefreshToken(credential.getRefreshToken());

      bigqueryclient = buildService(credential);
    }

    // Make API calls using your client.
    listDatasets(bigqueryclient, PROJECT_ID);

  }


  /**
   *  Builds an authorized BigQuery API client.
   */
  private static Bigquery buildService(Credential credential) {
    HttpTransport httpTransport = new NetHttpTransport();
    JacksonFactory jsonFactory = new JacksonFactory();

    return Bigquery.builder(httpTransport, jsonFactory)
        .setHttpRequestInitializer(credential)
        .build();
  }


  /**
   * Build an authorization flow and store it as a static class attribute.
   */
  static GoogleAuthorizationCodeFlow getFlow() {
    if (flow == null) {
      HttpTransport httpTransport = new NetHttpTransport();
      JacksonFactory jsonFactory = new JacksonFactory();

      flow = new GoogleAuthorizationCodeFlow.Builder(httpTransport,
          jsonFactory,
          clientSecrets,
          SCOPES)
      .setAccessType("offline").setApprovalPrompt("force").build();
    }
    return flow;
  }


  /**
   * Exchange the authorization code for OAuth 2.0 credentials.
   */
  static Credential exchangeCode(String authorizationCode) throws IOException  {
    GoogleAuthorizationCodeFlow flow = getFlow();
    GoogleTokenResponse response =
        flow.newTokenRequest(authorizationCode).setRedirectUri(REDIRECT_URI).execute();
    return flow.createAndStoreCredential(response, null); 
  } 

  /**
   * No need to go through OAuth dance, get an access token using the
   * existing refresh token.
   */
  public static GoogleCredential createCredentialWithRefreshToken(HttpTransport transport,
      JsonFactory jsonFactory, TokenResponse tokenResponse) {
    return new GoogleCredential.Builder().setTransport(transport)
        .setJsonFactory(jsonFactory)
        .setClientSecrets(clientSecrets)
        .build()
        .setFromTokenResponse(tokenResponse);
  }

  
  /**
   *  Helper to load client ID/Secret from file.
   */
  private static GoogleClientSecrets loadClientSecrets() {
    try {
      GoogleClientSecrets clientSecrets =
          GoogleClientSecrets.load(new JacksonFactory(),
              BigQueryOAuthSample.class.getResourceAsStream(CLIENTSECRETS_LOCATION));
      return clientSecrets;
    } catch (Exception e)  {
      System.out.println("Could not load clientsecrets.json");
      e.printStackTrace();
    }
    return clientSecrets;
  }


  /**
   *  Helper to store a new refresh token in token.properties file.
   */
  private static void storeRefreshToken(String refresh_token) {
    Properties properties = new Properties();
    properties.setProperty("refreshtoken", refresh_token);
    System.out.println(properties.get("refreshtoken"));
    try {
      properties.store(new FileOutputStream("token.properties"), null);
    } catch (FileNotFoundException e) {
      e.printStackTrace();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }


  /**
   *  Helper to load refresh token from the token.properties file.
   */
  private static String loadRefreshToken(){
    Properties properties = new Properties();
    try {
      properties.load(new FileInputStream("token.properties"));
    } catch (FileNotFoundException e) {
      e.printStackTrace();
    } catch (IOException e) {
      e.printStackTrace();
    }
    return (String) properties.get("refreshtoken");
  }  

  /**
   * 
   * List available Datasets.
   */
  public static void listDatasets(Bigquery bigquery, String projectId)
      throws IOException {
    Datasets.List datasetRequest = bigquery.datasets().list(projectId);
    DatasetList datasetList = datasetRequest.execute();
    if (datasetList.getDatasets() != null) {
      List<com.google.api.services.bigquery.model.DatasetList.Datasets> datasets = datasetList.getDatasets();
      System.out.println("Available datasets\n----------------");
      for (com.google.api.services.bigquery.model.DatasetList.Datasets dataset : datasets) {
        System.out.format("%s\n", dataset.getDatasetReference().getDatasetId());
      }
    }
  }


}