package it.fvaleri.example;

import com.arjuna.ats.arjuna.recovery.RecoveryManager;

public class RMFactory {
    public RecoveryManager createInstance() {
        RecoveryManager recoveryManager = RecoveryManager.manager(RecoveryManager.INDIRECT_MANAGEMENT);
        recoveryManager.startRecoveryManagerThread();
        return recoveryManager;
    }
}
