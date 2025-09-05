import * as cdk from 'aws-cdk-lib';
import { RestApi, LambdaIntegration } from 'aws-cdk-lib/aws-apigateway';
import { Construct } from 'constructs';
import * as lambda from 'aws-cdk-lib/aws-lambda';
import * as path from 'path';

export class WhatIfInvestedBackendStack extends cdk.Stack {
  constructor(scope: Construct, id: string, props?: cdk.StackProps) {
    super(scope, id, props);

    // CORS (Cross-Origin Resource Sharing) is a browser security feature that restricts web pages 
    // from making requests to a different domain than the one it was served from, unless explicitly allowed.
    // By enabling these "Access-Control-Allow-*" headers, we inform the browser that it's permitted 
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


    const coinbaseCreateChargeLambda = new lambda.Function(this, 'WhatIfIInvestedCoinbaseMerchantPaymentHandler', {
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

    const chargesResource = api.root.addResource('charges');
    chargesResource.addMethod('POST', new LambdaIntegration(coinbaseCreateChargeLambda), {
      methodResponses: [
        { statusCode: '200', responseParameters: apiCORSResponseParameters },
        { statusCode: '400', responseParameters: apiCORSResponseParameters },
        { statusCode: '500', responseParameters: apiCORSResponseParameters },
      ],
    });
  }
}
