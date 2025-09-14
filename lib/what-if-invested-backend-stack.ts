import * as cdk from 'aws-cdk-lib';
import { RestApi, LambdaIntegration, RequestValidator, Model } from 'aws-cdk-lib/aws-apigateway';
import { Construct } from 'constructs';
import * as lambda from 'aws-cdk-lib/aws-lambda';
import * as path from 'path';
import * as secretsmanager from 'aws-cdk-lib/aws-secretsmanager';
import * as ec2 from 'aws-cdk-lib/aws-ec2'; // Import EC2 module

export class WhatIfInvestedBackendStack extends cdk.Stack {
  constructor(scope: Construct, id: string, props?: cdk.StackProps) {
    super(scope, id, props);

    // Define a VPC for the Lambda function to ensure a static egress IP
    const vpc = new ec2.Vpc(this, 'CoinbaseProxyVPC', {
      maxAzs: 2, // Deploy to 2 Availability Zones for high availability
      natGateways: 1, // Create one NAT Gateway for outbound internet access
      subnetConfiguration: [
        {
          cidrMask: 24,
          name: 'public-subnet',
          subnetType: ec2.SubnetType.PUBLIC,
        },
        {
          cidrMask: 24,
          name: 'private-subnet',
          subnetType: ec2.SubnetType.PRIVATE_WITH_EGRESS, // Private subnets with NAT Gateway egress
        },
      ],
    });

    // Define the Coinbase API Secret
    const coinbaseApiSecret = new secretsmanager.Secret(this, 'CoinbaseApiSecret', {
      secretName: 'coinbase-exchange-api-keys',
      description: 'API keys for Coinbase Exchange',
      generateSecretString: {
        secretStringTemplate: JSON.stringify({
          apiKey: '',
          apiSecret: '',
          apiPassphrase: '',
        }),
        generateStringKey: 'apiKey',
      },
    });

    // CORS (Cross-Origin Resource Sharing) is a browser security feature that restricts web pages 
    // from making requests to a different domain than the one it was served from, unless explicitly allowed.
    // By enabling these "Access-Control-Allow-" headers, we inform the browser that it's permitted 
    // to make requests to this API endpoint from another origin (like our CloudFront-distributed frontend).
    // Without these headers, the browser would block cross-origin requests, preventing the frontend from 
    // interacting with this API.

    const apiCORSResponseParameters = {
      'method.response.header.Access-Control-Allow-Origin': true,
      'method.response.header.Access-Control-Allow-Headers': true,
      'method.response.header.Access-Control-Allow-Methods': true,
    }

    const api = new RestApi(this, 'WhatIfIInvestedApi', {
      restApiName: 'WhatIfIInvested API',
      description: 'API for WhatIfIInvested service.',
      defaultCorsPreflightOptions: {
        // allowOrigins: ['https://willitwork.net'],
        allowOrigins: ['http://localhost:3000'],
        allowMethods: ['POST', 'GET', 'OPTIONS'],
        allowHeaders: ['Content-Type', 'Authorization'],
        allowCredentials: true,
      },
      deployOptions: {
        tracingEnabled: false,
        throttlingRateLimit: 1,
        throttlingBurstLimit: 2,
      },
    });

    // Existing Lambda for Coinbase Commerce
    const coinbaseCreateChargeLambda = new lambda.Function(this, 'WhatIfIInvestedCoinbaseMerchantPaymentHandler', {
      functionName: 'CoinbaseMerchantPaymentHandler',
      runtime: lambda.Runtime.JAVA_17,
      code: lambda.Code.fromAsset(path.join(__dirname, '../backend-application-code/app/build/distributions/what-if-invested-backend.zip')),
      handler: 'org.handlers.CoinbaseMerchantPayments::handleRequest',
      memorySize: 512,
      timeout: cdk.Duration.seconds(30),
      environment: {
        COINBASE_COMMERCE_API_KEY_ENV: "Some API Key",
        // Used by backend to set Coinbase redirect/cancel URLs
        FRONTEND_BASE_URL: 'http://localhost:3000',
      },
    });

    const chargesResource = api.root.addResource('charges');
    chargesResource.addMethod('POST', new LambdaIntegration(coinbaseCreateChargeLambda), {
      methodResponses: [
        { statusCode: '200', responseParameters: apiCORSResponseParameters },
        { statusCode: '400', responseParameters: apiCORSResponseParameters },
        { statusCode: '500', responseParameters: apiCORSResponseParameters },
      ],
    });

    // New Lambda for Coinbase Exchange Proxy
    const coinbaseExchangeProxyLambda = new lambda.Function(this, 'CoinbaseExchangeProxyLambda', {
      functionName: 'CoinbaseExchangeProxy',
      runtime: lambda.Runtime.JAVA_17,
      code: lambda.Code.fromAsset(path.join(__dirname, '../backend-application-code/app/build/distributions/what-if-invested-backend.zip')),
      handler: 'org.handlers.CoinbaseExchangeProxy::handleRequest', 
      memorySize: 512,
      timeout: cdk.Duration.seconds(10),
      environment: {
        COINBASE_API_SECRET_ARN: coinbaseApiSecret.secretArn,
      },
      // VPC configuration for static egress IP
      vpc: vpc,
      vpcSubnets: {
        subnetType: ec2.SubnetType.PRIVATE_WITH_EGRESS,
      },
    });

    // Grant Lambda permissions to read the secret
    coinbaseApiSecret.grantRead(coinbaseExchangeProxyLambda);

    // Grant Lambda permissions to create network interfaces in the VPC
    coinbaseExchangeProxyLambda.connections.allowToAnyIpv4(ec2.Port.tcp(443), 'Allow outbound HTTPS to Coinbase');


    const coinbaseProxyResource = api.root.addResource('coinbase-proxy');
    coinbaseProxyResource.addMethod('POST', new LambdaIntegration(coinbaseExchangeProxyLambda), {
      methodResponses: [
        { statusCode: '200', responseParameters: apiCORSResponseParameters },
        { statusCode: '400', responseParameters: apiCORSResponseParameters },
        { statusCode: '500', responseParameters: apiCORSResponseParameters },
      ],
    });
  }
}
