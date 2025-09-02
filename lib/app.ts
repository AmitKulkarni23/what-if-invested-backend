import { App } from "aws-cdk-lib";
import { WhatIfInvestedBackendStack } from "./what-if-invested-backend-stack";

const app = new App();

new WhatIfInvestedBackendStack(app, 'WhatIfInvestedBackendStack');

app.synth();