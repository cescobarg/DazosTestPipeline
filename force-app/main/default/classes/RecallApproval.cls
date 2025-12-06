public class RecallApproval {

    public class InputWrapper {
        @InvocableVariable(required=true)
        public String workItemId;
    }

    @InvocableMethod(label='Recall Approval Process')
    public static void recallApproval(List<InputWrapper> requests) {
        for (InputWrapper req : requests) {
            Approval.ProcessWorkitemRequest approvalReq = new Approval.ProcessWorkitemRequest();
            approvalReq.setAction('Removed');
            approvalReq.setWorkitemId(Id.valueOf(req.workItemId));
            Approval.process(approvalReq);
        }
    }
}