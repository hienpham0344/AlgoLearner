package com.algolearner.platform.auth;

import com.algolearner.platform.admin.AccountAdministrationService;
import com.algolearner.platform.common.exception.ApiException;
import com.algolearner.platform.common.exception.ErrorCode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthControllerTest {
    private final AuthService service = mock(AuthService.class);
    private final AuthController controller = new AuthController(service, mock(AuthSessionService.class),
            mock(AccountAdministrationService.class));

    @Test
    void forgotPasswordConcealsCooldownAndMailFailureWithSameResponse() {
        var request = new AuthDtos.ForgotPasswordRequest("person@example.test");
        var expected = AuthService.resetRequestMessage(request);
        when(service.forgotPassword(request)).thenReturn(expected)
                .thenThrow(new ApiException(ErrorCode.OTP_RATE_LIMITED))
                .thenThrow(new ApiException(ErrorCode.MAIL_DELIVERY_FAILED))
                .thenThrow(new ApiException(ErrorCode.MAIL_NOT_CONFIGURED));

        for (int attempt = 0; attempt < 4; attempt++) {
            var response = controller.forgot(request);
            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getData()).isEqualTo(expected);
        }
    }

    @Test
    void resetResendConcealsCooldownButRegistrationResendKeepsItsError() {
        var reset = new AuthDtos.EmailRequest("person@example.test", null, "RESET_PASSWORD");
        var register = new AuthDtos.EmailRequest("person@example.test", null, "REGISTER");
        when(service.resend(reset)).thenThrow(new ApiException(ErrorCode.OTP_RATE_LIMITED));
        when(service.resend(register)).thenThrow(new ApiException(ErrorCode.OTP_RATE_LIMITED));

        var response = controller.resend(reset);
        assertThat(response.getData().success()).isTrue();
        assertThat(response.getData().message()).isEqualTo(AuthService.RESET_REQUEST_MESSAGE);
        assertThatThrownBy(() -> controller.resend(register)).isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.OTP_RATE_LIMITED.name()));
    }

    @Test
    void forgotPasswordStillRejectsInvalidRequest() {
        var request = new AuthDtos.ForgotPasswordRequest((String) null);
        when(service.forgotPassword(request)).thenThrow(new ApiException(ErrorCode.VALIDATION_FAILED));
        assertThatThrownBy(() -> controller.forgot(request)).isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.VALIDATION_FAILED.name()));
    }
}
