
def createZookeeperFixedStack(String region, String stack) {
    sh "aws cloudformation --region ${region} validate-template --template-body file://zk-fixed-resources.json"
    sh "aws cloudformation --region ${region} create-stack --stack-name ${stack} --template-body \
            file://zk-fixed-resources.json --parameters file://parameters/zk-fixed-resources-param.json"
    sh "aws cloudformation --region ${region} wait stack-create-complete --stack-name ${stack}"
    sh "aws cloudformation --region ${region} describe-stack-events --stack-name ${stack} \
        --query 'StackEvents[].[{Resource:LogicalResourceId,Status:ResourceStatus,Reason:ResourceStatusReason}]' \
            --output table"
}

def createKafkaFixedStack(String region, String stack) {
    sh "aws cloudformation --region ${region} validate-template --template-body file://kafka-fixed-resources.json"
    sh "aws cloudformation --region ${region} create-stack --stack-name ${stack} --template-body \
            file://kafka-fixed-resources.json --parameters file://parameters/kafka-fixed-resources-param.json"
    sh "aws cloudformation --region ${region} wait stack-create-complete --stack-name ${stack}"
    sh "aws cloudformation --region ${region} describe-stack-events --stack-name ${stack} \
        --query 'StackEvents[].[{Resource:LogicalResourceId,Status:ResourceStatus,Reason:ResourceStatusReason}]' \
            --output table"
}

def createZookeeperClusterStack(String region, String stack) {
    sh "aws cloudformation --region ${region} validate-template --template-body file://zk-cluster-resources.json"
    sh "aws cloudformation --region ${region} create-stack --stack-name ${stack} --template-body \
        file://zk-cluster-resources.json --parameters file://parameters/zk-cluster-resources-param.json"
    sh "aws cloudformation --region ${region} wait stack-create-complete --stack-name ${stack}"
    sh "aws cloudformation --region ${region} describe-stack-events --stack-name ${stack} \
        --query 'StackEvents[].[{Resource:LogicalResourceId,Status:ResourceStatus,Reason:ResourceStatusReason}]' \
        --output table"
}

def createKafkaClusterStack(String region, String stack) {
    sh "aws cloudformation --region ${region} validate-template --template-body file://kafka-cluster-resources.json"
    sh "aws cloudformation --region ${region} create-stack --stack-name ${stack} --template-body \
        file://kafka-cluster-resources.json --parameters file://parameters/kafka-cluster-resources-param.json"
    sh "aws cloudformation --region ${region} wait stack-create-complete --stack-name ${stack}"
    sh "aws cloudformation --region ${region} describe-stack-events --stack-name ${stack} \
        --query 'StackEvents[].[{Resource:LogicalResourceId,Status:ResourceStatus,Reason:ResourceStatusReason}]' \
        --output table"
}


pipeline {
    agent any

    parameters {
        string(name: 'REGION', defaultValue: 'us-east-1', description: 'worspace to use in Terraform')
        string(name: 'ZK_FX_STACK', defaultValue: 'zk-fixed-resources', description: 'worspace to use in Terraform')
        string(name: 'ZK_CLUSTER_STACK', defaultValue: 'zk-cluster-resources', description: 'worspace to use in Terraform')
        string(name: 'KAFKA_FX_STACK', defaultValue: 'kafka-fixed-resources', description: 'worspace to use in Terraform')
        string(name: 'KAFKA_CLUSTER_STACK', defaultValue: 'kafka-cluster-resources', description: 'worspace to use in Terraform')
    }
    stages {
        stage('Create-or-Destroy!') {
            steps {
                script {
                    def apply = false
                    try {
                        input message: 'confirm apply', ok: 'Apply config'
                        apply = true;
                    } catch (err) {
                        apply = false
                        sh "aws cloudformation delete-stack --stack-name ${params.ZK_FX_STACK} --region ${params.REGION}"
                        sh "aws cloudformation --region ${params.REGION} wait stack-delete-complete --stack-name ${params.ZK_FX_STACK}"
                        sh "aws cloudformation delete-stack --stack-name ${params.KAFKA_FX_STACK} --region ${params.REGION}"
                        sh "aws cloudformation --region ${params.REGION} wait stack-delete-complete --stack-name ${params.KAFKA_FX_STACK}"
                        sh "aws cloudformation delete-stack --stack-name ${params.ZK_CLUSTER_STACK} --region ${params.REGION}"
                        sh "aws cloudformation --region ${params.REGION} wait stack-delete-complete --stack-name ${params.ZK_CLUSTER_STACK}"
                        sh "aws cloudformation delete-stack --stack-name ${params.KAFKA_CLUSTER_STACK} --region ${params.REGION}"
                        sh "aws cloudformation --region ${params.REGION} wait stack-delete-complete --stack-name ${params.KAFKA_CLUSTER_STACK}"
                    }
                    if (apply) {
                        sh "echo Creating AWS infrastructure......!"
                    }
                }
            }
        }
        stage('zookeeper-fixed-resources-infra') {
            steps {
                dir('aws-cloudformation/infra/zookeeper-cluster/') {
                    script {
                        def apply = null
                        def status = null
                        try {
                            status = sh(script: "aws cloudformation describe-stacks --region ${params.REGION} \
                            --stack-name ${params.ZK_FX_STACK} --query Stacks[0].StackStatus --output text", returnStdout: true)
                            apply = true
                            sh "echo $status"
                            if ($status == 'DELETE_FAILED' || 'ROLLBACK_COMPLETE' || 'ROLLBACK_FAILED' || 'UPDATE_ROLLBACK_FAILED') {
                                sh "aws cloudformation delete-stack --stack-name ${params.ZK_FX_STACK} --region ${params.REGION}"
                                sh 'echo Creating Zookeeper fixed resource cluster Cluster....'
                                createZookeeperClusterStack(params.REGION, params.ZK_FX_STACK)
                                apply = false
                            }
                        } catch (ignored) {
                            apply = false
                            sh "echo Creating Zookeeper fixed resource Cluster"
                            createZookeeperFixedStack(params.REGION, params.ZK_FX_STACK)
                        }
                        if (apply) {
                            try {
                                sh "echo Stack exists, attempting update..."
                                sh "aws cloudformation --region ${params.REGION} update-stack --stack-name \
                                    ${params.ZK_FX_STACK} --template-body file://zk-fixed-resources.json \
                                    --parameters file://parameters/zk-fixed-resources-param.json"
                            } catch (err) {
                                sh "echo Finished create/update - no updates to be performed"
                            }
                        }
                        sh "echo Finished create/update successfully!"
                    }
                }
            }
        }
        stage('kafka-fixed-resources-infra') {
            steps {
                dir('aws-cloudformation/infra/kafka-cluster/') {
                    script {
                        def apply = null
                        def status = null
                        try {
                            status = sh(script: "aws cloudformation describe-stacks --region ${params.REGION} --stack-name \
                            ${params.KAFKA_FX_STACK} --query Stacks[0].StackStatus --output text", returnStdout: true)
                            apply = true
                            sh "echo $status"
                            if ($status == 'DELETE_FAILED' || 'ROLLBACK_COMPLETE' || 'ROLLBACK_FAILED' || 'UPDATE_ROLLBACK_FAILED') {
                                sh "aws cloudformation delete-stack --stack-name ${params.KAFKA_FX_STACK} --region ${params.REGION}"
                                sh 'echo Creating kafka fixed resource Cluster....'
                                createKafkaFixedStack(params.REGION, params.KAFKA_FX_STACK)
                                apply = false
                            }
                        } catch (err) {
                            apply = false
                            sh 'echo Creating Kafka Fixed resources....'
                            createKafkaFixedStack(params.REGION, params.KAFKA_FX_STACK)
                        }
                        if (apply) {
                            try {
                                sh "echo Stack exists, attempting update..."
                                sh "aws cloudformation --region ${params.REGION} update-stack --stack-name \
                                    ${params.KAFKA_FX_STACK} --template-body file://kafka-fixed-resources.json \
                                    --parameters file://parameters/kafka-fixed-resources-param.json"
                            } catch (error) {
                                sh "echo Finished create/update - no updates to be performed"
                            }
                        }
                        sh "echo Finished create/update successfully!"
                    }
                }
            }
        }
        stage('zookeeper-cluster-infra') {
            steps {
                dir('aws-cloudformation/infra/zookeeper-cluster/') {
                    script {
                        def apply = null
                        def status = null
                        try {
                            status = sh(script: "aws cloudformation describe-stacks --region ${params.REGION} \
                                --stack-name ${params.ZK_CLUSTER_STACK} --query Stacks[0].StackStatus --output text", returnStdout: true)
                            apply = true
                            sh "echo $status"
                            if (status == 'DELETE_FAILED' || 'ROLLBACK_COMPLETE' || 'ROLLBACK_FAILED' || 'UPDATE_ROLLBACK_FAILED') {
                                sh "aws cloudformation delete-stack --stack-name ${params.ZK_CLUSTER_STACK} --region ${params.REGION}"
                                sh 'echo Creating Zookeeper Cluster....'
                                createZookeeperClusterStack(params.REGION, params.ZK_CLUSTER_STACK)
                                apply = false
                            }
                        } catch (err) {
                            apply = false
                            sh 'echo Creating Zookeeper Cluster for first time....'
                            createZookeeperClusterStack(params.REGION, params.ZK_CLUSTER_STACK)
                        }
                        if (apply) {
                            try {
                                sh "echo Stack exists, attempting update..."
                                sh "aws cloudformation --region ${params.REGION} update-stack --stack-name \
                                    ${params.ZK_CLUSTER_STACK} --template-body file://zk-cluster-resources.json \
                                    --parameters file://parameters/zk-cluster-resources-param.json"
                            } catch (err) {
                                sh "echo Finished create/update - no updates to be performed"
                            }
                        }
                        sh "echo Finished create/update successfully!"
                    }
                }
            }
        }
        stage('kafka-cluster-infra') {
            steps {
                dir('aws-cloudformation/infra/kafka-cluster/') {
                    script {
                        def apply = null
                        def status = null
                        try {
                            status = sh(script: "aws cloudformation describe-stacks --region ${params.REGION} \
                                --stack-name ${params.KAKFA_CLUSTER_STACK} --query Stacks[0].StackStatus --output text", returnStdout: true)
                            apply = true
                            sh "echo $status"
                            if (status == 'DELETE_FAILED' || 'ROLLBACK_COMPLETE' || 'ROLLBACK_FAILED' || 'UPDATE_ROLLBACK_FAILED') {
                                sh "aws cloudformation delete-stack --stack-name ${params.KAKFA_CLUSTER_STACK} --region ${params.REGION}"
                                sh 'echo Creating Zookeeper Cluster....'
                                createKafkaClusterStack(params.REGION, params.KAKFA_CLUSTER_STACK)
                                apply = false
                            }
                        } catch (err) {
                            apply = false
                            sh 'echo Creating Zookeeper Cluster for first time....'
                            createKafkaClusterStack(params.REGION, params.KAKFA_CLUSTER_STACK)
                        }
                        if (apply) {
                            try {
                                sh "echo Stack exists, attempting update..."
                                sh "aws cloudformation --region ${params.REGION} update-stack --stack-name \
                                    ${params.KAKFA_CLUSTER_STACK} --template-body file://zk-cluster-resources.json \
                                    --parameters file://parameters/zk-cluster-resources-param.json"
                            } catch (err) {
                                sh "echo Finished create/update - no updates to be performed"
                            }
                        }
                        sh "echo Finished create/update successfully!"
                    }
                }
            }
        }
    }
}