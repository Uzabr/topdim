package uz.topdim.identity.security;

public interface SmsSender {
    void sendOtp(String phone, String code);
}
