global with sharing class UserInfoController {

  private final SObject standardCtrlRecord;

  public UserInfoController(ApexPages.StandardController stdController) {
    this.standardCtrlRecord = stdController.getRecord(); // Now it's an extension
  }

  public class CTMTokenException extends Exception {}


  public class CTMAuthResponse {
    public String email;
    public String token;
    public String sessionId;
    public String error;
    public String validUntil;

    public Map<String,String> toMap() {
      return new Map<String,String>{
        'email'     => email,
        'token'     => token,
        'sessionId' => sessionId,
        'error'     => error,
        'valid_until' => validUntil
      };
    }
  }

  private static String CTMHost() { return 'api.calltrackingmetrics.com'; }
  private static String CTMAccount() { return '277394'; }
  private static String CTMInstanceSecret() {
    CTMDevice__c device = [
        SELECT SecretKey__c
        FROM CTMDevice__c
        WHERE Active__c = true
        LIMIT 1
    ];
    return device.SecretKey__c;
  }

  @RemoteAction
  global static String getCurrentUserEmail(String info) {
    return UserInfo.getUserEmail();
  }

  webService static String getDeviceId() {
    CTMDevice__c device = [
        SELECT Id
        FROM CTMDevice__c
        WHERE Active__c = true
        LIMIT 1
    ];
    return device.Id;
  }

  webService static String getRemoteAccessToken() {
    // call getCTMAccessToken
    return getCTMAccessToken('info=ctm');
  }

  @RemoteAction // webService
  global static String getCTMAccessToken(String info) {
    Http         http = new Http();
    HttpRequest  req  = prepareRequest();
    HttpResponse res  = http.send(req);

    CTMAuthResponse response = new CTMAuthResponse();

    response.email = UserInfo.getUserEmail();
    response.sessionId = UserInfo.getSessionId();

    if (res.getStatusCode() == 200) {
      Map<String, Object> resBody = (Map<String, Object>) JSON.deserializeUntyped(res.getBody());
      response.token = (String) resBody.get('token');
      response.validUntil = String.valueOf(resBody.get('valid_until'));
    } else if (res.getStatusCode() == 401) {
      response.token = null;
      response.validUntil = null;
      response.error = res.getBody();
    } else {
      throw new CTMTokenException('Error retrieving access token: ' + res.getBody());
    }

    return JSON.serialize(response.toMap());
  }

  private static HttpRequest prepareRequest() {
    HttpRequest req = new HttpRequest();
    Map<String, String> params = new Map<String, String>{
      'id'         => UserInfo.getUserId(),
      'name'       => UserInfo.getUserName(),
      'first_name' => UserInfo.getFirstName(),
      'last_name'  => UserInfo.getLastName(),
      'session_id' => UserInfo.getSessionId(),
      'email'      => UserInfo.getUserEmail(),
      'instanceId' => UserInfo.getOrganizationId(),
      'zone'       => UserInfo.getTimeZone().getID(),
      'lang'       => UserInfo.getLanguage(),
      'locale'     => UserInfo.getLocale()
    };
    String payload    = JSON.serialize(params);
    String authheader ='Salesforce ' + CTMInstanceSecret();

    req.setEndpoint('https://' + CTMHost() + '/api/v1/accounts/' + CTMAccount() + '/salesforce_access');
    req.setHeader('Content-Type', 'application/json');
    req.setHeader('Authorization', authheader);
    req.setMethod('POST');

    req.setBody(payload);
    return req;
  }
}