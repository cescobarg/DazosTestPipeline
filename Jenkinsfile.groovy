#!groovy
import groovy.json.JsonSlurperClassic

node {
    // -----------------------------
    // Environment variables
    // -----------------------------
    def SF_CONSUMER_KEY = env.SF_CONSUMER_KEY
    def SF_USERNAME = env.SF_USERNAME
    def SERVER_KEY_CREDENTALS_ID = env.SERVER_KEY_CREDENTALS_ID
    def SF_INSTANCE_URL = env.SF_INSTANCE_URL ?: "https://login.salesforce.com"
    def toolbelt = tool 'toolbelt'

    def branchName = env.BRANCH_NAME ?: 'Prod'

    stage('Checkout Source') {
        checkout scm
    }

    withEnv(["HOME=${env.WORKSPACE}"]) {
        withCredentials([file(credentialsId: SERVER_KEY_CREDENTALS_ID, variable: 'server_key_file')]) {

            // -----------------------------
            // Authorize Dev Hub
            // -----------------------------
            stage('Authorize Dev Hub') {
                def rc = command "\"${toolbelt}/sf\" org login jwt --instance-url ${SF_INSTANCE_URL} --client-id ${SF_CONSUMER_KEY} --username ${SF_USERNAME} --jwt-key-file ${server_key_file} --set-default-dev-hub --alias Dazos_DevHub"
                if (rc != 0) error 'Salesforce Dev Hub authorization failed.'
            }

            // -----------------------------
            // Determine workflow based on branch
            // -----------------------------
            if (branchName == 'QA') {
                qaFlow(toolbelt)
            } else if (branchName == 'Staging') {
                stagingFlow(toolbelt)
            } else if (branchName == 'Prod') {
                prodFlow(toolbelt)
            } else {
                echo "Branch ${branchName} detected — running scratch org tests only."
                scratchOrgFlow(toolbelt)
            }
        }
    }
}

// -----------------------------
// SCRATCH ORG FLOW (for dev / testing)
// -----------------------------
def scratchOrgFlow(toolbelt) {
    stage('Create Scratch Org') {
        def rc = command "\"${toolbelt}/sf\" org create scratch --target-dev-hub Dazos_DevHub --set-default --definition-file config/project-scratch-def.json --alias TestScratchOrg --wait 10 --duration-days 1"
        if (rc != 0) error 'Scratch org creation failed.'
    }

    stage('Push to Scratch Org') {
        def rc = command "\"${toolbelt}/sf\" project deploy start --target-org TestScratchOrg"
        if (rc != 0) error 'Deployment to scratch org failed.'
    }

    stage('Run Apex Tests') {
        def rc = command "\"${toolbelt}/sf\" apex run test --target-org TestScratchOrg --wait 10 --result-format tap --code-coverage --test-level RunLocalTests"
        if (rc != 0) error 'Unit tests in scratch org failed.'
    }

    stage('Delete Scratch Org') {
        def rc = command "\"${toolbelt}/sf\" org delete scratch --target-org TestScratchOrg --no-prompt"
        if (rc != 0) error 'Scratch org deletion failed.'
    }
}

// -----------------------------
// QA FLOW
// -----------------------------
def qaFlow(toolbelt) {
    scratchOrgFlow(toolbelt) // create scratch org, deploy, run tests, delete

    stage('Static Checks') {
        // Add PMD/ESLint commands here
        echo "Running PMD/ESLint checks..."
        // Example: command("eslint force-app/main/default/lwc/**/*.js")
    }

    stage('Deploy to QA Sandbox') {
        def rc = command "\"${toolbelt}/sf\" project deploy start --target-org Dazos_DevHub"
        if (rc != 0) error 'Deployment to QA failed.'
    }

    stage('Run Tests in QA') {
        def rc = command "\"${toolbelt}/sf\" apex run test --target-org Dazos_DevHub --wait 10 --result-format tap --code-coverage --test-level RunLocalTests"
        if (rc != 0) error 'QA tests failed.'
    }
}

// -----------------------------
// STAGING FLOW
// -----------------------------
def stagingFlow(toolbelt) {
    scratchOrgFlow(toolbelt) // create scratch org, deploy, run tests, delete

    stage('Deploy to Full Sandbox') {
        def rc = command "\"${toolbelt}/sf\" project deploy start --target-org Dazos_DevHub"
        if (rc != 0) error 'Deployment to Full Sandbox failed.'
    }

    stage('Run Complete Test Suite in Full') {
        def rc = command "\"${toolbelt}/sf\" apex run test --target-org Dazos_DevHub --wait 30 --result-format tap --code-coverage --test-level RunAllTestsInOrg"
        if (rc != 0) error 'Full sandbox tests failed.'
    }
}

// -----------------------------
// PRODUCTION FLOW
// -----------------------------
def prodFlow(toolbelt) {
    scratchOrgFlow(toolbelt) // create scratch org, deploy, run tests, delete

    stage('Manual Approval for Production') {
        input message: "Approve deployment to Production?", ok: "Deploy"
    }

    stage('Deploy to Production') {
        def rc = command("\"${toolbelt}/sf\" project deploy start --target-org Prod_Alias --wait 60 --result-format tap --code-coverage --test-level RunAllTestsInOrg")
        if (rc != 0) error 'Production deployment failed.'
    }
}

// -----------------------------
// Helper function to run shell/bat commands
// -----------------------------
def command(script) {
    if (isUnix()) {
        return sh(returnStatus: true, script: script)
    } else {
        return bat(returnStatus: true, script: script)
    }
}
