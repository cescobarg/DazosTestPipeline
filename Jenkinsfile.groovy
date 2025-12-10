#!groovy
import groovy.json.JsonSlurperClassic

node {

    // ---------------------------------------------------
    // ENVIRONMENT VARIABLES FROM JENKINS
    // ---------------------------------------------------
    def SF_CONSUMER_KEY        = env.SF_CONSUMER_KEY
    def SF_USERNAME            = env.SF_USERNAME
    def SERVER_KEY_CRED_ID     = env.SERVER_KEY_CREDENTALS_ID
    def SF_INSTANCE_URL        = env.SF_INSTANCE_URL ?: "https://login.salesforce.com"
    def toolbelt               = tool 'toolbelt'

    def branchName             = env.BRANCH_NAME ?: 'local'

    stage('Checkout Source') {
        checkout scm
    }

    // HOME must be workspace on Windows for sf CLI
    withEnv(["HOME=${env.WORKSPACE}"]) {

        // Load the JWT key file
        withCredentials([file(credentialsId: SERVER_KEY_CRED_ID, variable: 'server_key_file')]) {

            // ---------------------------------------------------
            // AUTHORIZE DEV HUB
            // ---------------------------------------------------
            stage('Authorize Dev Hub') {
                // Convert Jenkins tool path to windows-friendly path
                def sfPath = "${toolbelt}/sf".replace("/", "\\")

                def loginCmd = """
                    "${sfPath}" org login jwt ^
                        --instance-url "${SF_INSTANCE_URL}" ^
                        --client-id "${SF_CONSUMER_KEY}" ^
                        --username "${SF_USERNAME}" ^
                        --jwt-key-file "${server_key_file}" ^
                        --set-default-dev-hub ^
                        --alias Dazos_DevHub
                """

                def rc = command(loginCmd)

                if (rc != 0) error 'Salesforce Dev Hub authorization failed.'
            }

            // Map branches -> deployment flows
            if (branchName == 'QA') {
                qaFlow(toolbelt)

            } else if (branchName == 'Staging') {
                stagingFlow(toolbelt)

            } else if (branchName == 'Prod') {
                prodFlow(toolbelt)

            } else {
                echo "Branch ${branchName} detected — running Scratch Org workflow only."
                scratchOrgFlow(toolbelt)
            }
        }
    }
}

// ---------------------------------------------------
// SCRATCH ORG WORKFLOW
// ---------------------------------------------------
def scratchOrgFlow(toolbelt) {

    def sfPath = "${toolbelt}/sf".replace("/", "\\")

    stage('Create Scratch Org') {
        def cmd = """
            "${sfPath}" org create scratch ^
                --target-dev-hub Dazos_DevHub ^
                --set-default ^
                --definition-file config/project-scratch-def.json ^
                --alias TestScratchOrg ^
                --wait 10 ^
                --duration-days 1
        """
        if (command(cmd) != 0) error 'Scratch org creation failed.'
    }

    stage('Push to Scratch Org') {
        def cmd = """
            "${sfPath}" project deploy start ^
                --target-org TestScratchOrg
        """
        if (command(cmd) != 0) error 'Deployment to scratch org failed.'
    }

    stage('Run Apex Tests') {
        def cmd = """
            "${sfPath}" apex run test ^
                --target-org TestScratchOrg ^
                --wait 10 ^
                --result-format tap ^
                --code-coverage ^
                --test-level RunLocalTests
        """
        if (command(cmd) != 0) error 'Unit tests failed.'
    }

    stage('Delete Scratch Org') {
        def cmd = """
            "${sfPath}" org delete scratch ^
                --target-org TestScratchOrg ^
                --no-prompt
        """
        if (command(cmd) != 0) error 'Scratch org deletion failed.'
    }
}

// ---------------------------------------------------
// QA FLOW
// ---------------------------------------------------
def qaFlow(toolbelt) {
    def sfPath = "${toolbelt}/sf".replace("/", "\\")

    scratchOrgFlow(toolbelt)

    stage('Static Checks') {
        echo "Run PMD/ESLint here"
    }

    stage('Deploy to QA Sandbox') {
        def cmd = """
            "${sfPath}" project deploy start ^
                --target-org DazosScratch
        """
        if (command(cmd) != 0) error 'Deployment to QA failed.'
    }

    stage('Run Tests in QA Sandbox') {
        def cmd = """
            "${sfPath}" apex run test ^
                --target-org DazosScratch ^
                --wait 10 ^
                --result-format tap ^
                --code-coverage ^
                --test-level RunLocalTests
        """
        if (command(cmd) != 0) error 'QA tests failed.'
    }
}

// ---------------------------------------------------
// STAGING FLOW
// ---------------------------------------------------
def stagingFlow(toolbelt) {
    def sfPath = "${toolbelt}/sf".replace("/", "\\")

    scratchOrgFlow(toolbelt)

    stage('Deploy to Full Sandbox') {
        def cmd = """
            "${sfPath}" project deploy start ^
                --target-org Full_Sandbox_Alias
        """
        if (command(cmd) != 0) error 'Deployment to Full sandbox failed.'
    }

    stage('Run Tests in Full Sandbox') {
        def cmd = """
            "${sfPath}" apex run test ^
                --target-org Full_Sandbox_Alias ^
                --wait 30 ^
                --result-format tap ^
                --code-coverage ^
                --test-level RunAllTestsInOrg
        """
        if (command(cmd) != 0) error 'Full sandbox tests failed.'
    }
}

// ---------------------------------------------------
// PRODUCTION FLOW
// ---------------------------------------------------
def prodFlow(toolbelt) {
    def sfPath = "${toolbelt}/sf".replace("/", "\\")

    scratchOrgFlow(toolbelt)

    stage('Manual Approval for Production') {
        input message: "Approve deployment to Production?", ok: "Deploy"
    }

    stage('Deploy to Production') {
        def cmd = """
            "${sfPath}" project deploy start ^
                --target-org Prod_Alias ^
                --wait 60 ^
                --result-format tap ^
                --code-coverage ^
                --test-level RunAllTestsInOrg
        """
        if (command(cmd) != 0) error 'Production deployment failed.'
    }
}

// ---------------------------------------------------
// CROSS-PLATFORM COMMAND WRAPPER
// ---------------------------------------------------
def command(script) {
    if (isUnix()) {
        return sh(returnStatus: true, script: script)
    } else {
        return bat(returnStatus: true, script: script)
    }
}
