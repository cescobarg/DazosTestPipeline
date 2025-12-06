/*
 * © 2011-2025 CallTrackingMetrics, LLC. All rights reserved.
 *
 * CallTrackingMetrics®, GeoContact®, Teluv® and FormReactor® are the registered trademarks of CallTrackingMetrics, LLC.
 */
@isTest
private class UserInfoControllerTest {

    private class MockHttpResponse implements HttpCalloutMock {
        // Implement this interface method
        public HTTPResponse respond(HTTPRequest req) {
            // Create a fake response
            HttpResponse res = new HttpResponse();
            res.setHeader('Content-Type', 'application/json');
            res.setBody('{"status": "ok", "token": "mockToken"}'); // Adjust the body as needed for your tests
            res.setStatusCode(200);
            return res;
        }
    }

    static testMethod void testGetCTMAccessToken() {
        // Create test data for CTMDevice__c with a SecretKey__c
        CTMDevice__c testDevice = new CTMDevice__c(Name='Test Device', SecretKey__c='TestSecret', Active__c=true);
        insert testDevice;

        // Set mock response
        Test.setMock(HttpCalloutMock.class, new MockHttpResponse());

        // Start test execution context
        Test.startTest();

        // Now make your callout and assert results
        String response = UserInfoController.getCTMAccessToken('someInfo');

        // End test execution context
        Test.stopTest();

        // Deserialize the response for easier assertion
        UserInfoController.CTMAuthResponse authResponse = (UserInfoController.CTMAuthResponse)JSON.deserialize(response, UserInfoController.CTMAuthResponse.class);

        // Assert statements to ensure that the method behaves as expected
        System.assert(authResponse != null);
        System.assertEquals('mockToken', authResponse.token);
        // Add more assertions as needed
    }

}