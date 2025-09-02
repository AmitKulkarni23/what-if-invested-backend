import * as cdk from 'aws-cdk-lib';
import { RestApi, LambdaIntegration } from 'aws-cdk-lib/aws-apigateway';
import { Construct } from 'constructs';
import * as lambda from 'aws-cdk-lib/aws-lambda';
import * as path from 'path';

export class WhatIfInvestedBackendStack extends cdk.Stack {
  constructor(scope: Construct, id: string, props?: cdk.StackProps) {
    super(scope, id, props);

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


    const coinbaseMerchantPaymentHandler = new lambda.Function(this, 'WhatIfIInvestedCoinbaseMerchantPaymentHandler', {
      functionName: 'CoinbaseMerchantPaymentHandler',
      runtime: lambda.Runtime.JAVA_17,
      code: lambda.Code.fromAsset(path.join(__dirname, '../backend-application-code/app/build/distributions/what-if-invested-backend.zip')),
      handler: 'org.handlers.CoinbaseMerchantPayments::handleRequest',
      memorySize: 512,
      timeout: cdk.Duration.seconds(30),
      environment: {
        COINBASE_COMMERCE_API_KEY_ENV: "Some API Key",
      },
    });

    api.root.addMethod('POST', new LambdaIntegration(coinbaseMerchantPaymentHandler));



  }
}
