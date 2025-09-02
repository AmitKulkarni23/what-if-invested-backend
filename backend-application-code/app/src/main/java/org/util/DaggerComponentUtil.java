package org.util;

import org.dagger.DaggerServiceComponent;
import org.dagger.ServiceComponent;

public class DaggerComponentUtil {
    public static ServiceComponent create() {
        return DaggerServiceComponent.create();
    }
}
