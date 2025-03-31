const AWS = require('aws-sdk');
const ecs = new AWS.ECS();

exports.handler = async (event) => {
    console.log('Event:', JSON.stringify(event));

    try {
        // 필수 파라미터 확인
        if (!event.testId || !event.targetUrl || !event.virtualUsers || !event.durationSeconds) {
            throw new Error('Required parameters missing');
        }

        // 컨테이너 인덱스와 총 컨테이너 수 확인
        const containerIndex = event.containerIndex !== undefined ? event.containerIndex : 0;
        const totalContainers = event.totalContainers !== undefined ? event.totalContainers : 1;

        const params = {
            cluster: process.env.ECS_CLUSTER,
            taskDefinition: process.env.TASK_DEFINITION,
            launchType: 'FARGATE',
            networkConfiguration: {
                awsvpcConfiguration: {
                    subnets: [process.env.SUBNET_ID],
                    securityGroups: [process.env.SECURITY_GROUP_ID],
                    assignPublicIp: 'ENABLED'
                }
            },
            overrides: {
                containerOverrides: [
                    {
                        name: 'k6-runner',
                        environment: [
                            { name: 'TARGET_URL', value: event.targetUrl },
                            { name: 'VIRTUAL_USERS', value: event.virtualUsers.toString() },
                            { name: 'DURATION_SECONDS', value: event.durationSeconds.toString() },
                            { name: 'RAMP_UP_SECONDS', value: event.rampUpSeconds ? event.rampUpSeconds.toString() : '0' },
                            { name: 'TEST_ID', value: event.testId.toString() },
                            { name: 'SCRIPT_CONTENT', value: event.scriptContent || '' },
                            { name: 'CONTAINER_INDEX', value: containerIndex.toString() },
                            { name: 'TOTAL_CONTAINERS', value: totalContainers.toString() }
                        ]
                    }
                ]
            }
        };

        console.log(`Starting ECS task for test ID ${event.testId}, container ${containerIndex}/${totalContainers}`);
        const result = await ecs.runTask(params).promise();
        console.log('Task started:', JSON.stringify(result));

        if (!result.tasks || result.tasks.length === 0) {
            throw new Error('Failed to start ECS task');
        }

        return {
            statusCode: 200,
            taskId: result.tasks[0].taskArn,
            containerIndex: containerIndex,
            message: `Task started successfully for container ${containerIndex}/${totalContainers}`
        };
    } catch (error) {
        console.error('Error:', error);
        return {
            statusCode: 500,
            error: error.message
        };
    }
};