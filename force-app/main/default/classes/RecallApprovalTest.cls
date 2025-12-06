@isTest
public class RecallApprovalTest {
    
    @isTest
    static void testRecallApproval() {
        // Create a test Account and Opportunity (required for Quote)
        Account acct = new Account(Name = 'Test Account');
        insert acct;

        Opportunity opp = new Opportunity(
            Name = 'Test Opportunity',
            StageName = 'Prospecting',
            CloseDate = Date.today().addDays(30),
            AccountId = acct.Id
        );
        insert opp;

        // Create a test Quote
        Quote quote = new Quote(
            Name = 'Test Quote',
            OpportunityId = opp.Id,
            Status = 'Draft'
        );
        insert quote;

        // Submit Quote for Approval
        Approval.ProcessSubmitRequest req = new Approval.ProcessSubmitRequest();
        req.setObjectId(quote.Id);
        req.setSubmitterId(UserInfo.getUserId());
        req.setComments('Submitting for approval');
        Approval.ProcessResult result = Approval.process(req);
        System.assert(result.isSuccess(), 'Approval submission failed');

        // Get ProcessInstanceWorkitem
        List<ProcessInstanceWorkitem> items = [
            SELECT Id, ProcessInstanceId
            FROM ProcessInstanceWorkitem
            WHERE ProcessInstance.TargetObjectId = :quote.Id
            LIMIT 1
        ];
        System.assert(!items.isEmpty(), 'No ProcessInstanceWorkitem found');

        // Wrap the work item in our InputWrapper class
        RecallApproval.InputWrapper wrapper = new RecallApproval.InputWrapper();
        wrapper.workItemId = items[0].Id;

        List<RecallApproval.InputWrapper> inputList = new List<RecallApproval.InputWrapper> { wrapper };

        // Call the Apex method to recall approval
        Test.startTest();
        RecallApproval.recallApproval(inputList);
        Test.stopTest();

        // Verify the approval has been recalled
        ProcessInstance pi = [
            SELECT Status
            FROM ProcessInstance
            WHERE Id = :items[0].ProcessInstanceId
        ];
        System.assertEquals('Removed', pi.Status, 'Approval was not recalled');
    }
}