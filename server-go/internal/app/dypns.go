package app

import (
	"context"
	"fmt"
	"os"
	"strings"
	"time"

	openapi "github.com/alibabacloud-go/darabonba-openapi/v2/client"
	dypnsapi "github.com/alibabacloud-go/dypnsapi-20170525/v3/client"
	"github.com/alibabacloud-go/tea/dara"
)

type DypnsClient struct {
	client        *dypnsapi.Client
	schemeCode    string
	packageName   string
	packageSign   string
	smsSignName   string
	smsTemplate   string
	smsParam      string
	smsSchemeName string
}

func NewDypnsClientFromEnv() (*DypnsClient, error) {
	accessKeyID := strings.TrimSpace(firstNonEmpty(
		os.Getenv("DYPNS_ACCESS_KEY_ID"),
		os.Getenv("ALIYUN_DYPNS_ACCESS_KEY_ID"),
		os.Getenv("ALIBABA_CLOUD_ACCESS_KEY_ID"),
	))
	accessKeySecret := strings.TrimSpace(firstNonEmpty(
		os.Getenv("DYPNS_ACCESS_KEY_SECRET"),
		os.Getenv("ALIYUN_DYPNS_ACCESS_KEY_SECRET"),
		os.Getenv("ALIBABA_CLOUD_ACCESS_KEY_SECRET"),
	))
	if accessKeyID == "" || accessKeySecret == "" {
		return &DypnsClient{}, nil
	}
	regionID := strings.TrimSpace(firstNonEmpty(os.Getenv("DYPNS_REGION_ID"), "cn-hangzhou"))
	client, err := dypnsapi.NewClient(&openapi.Config{
		AccessKeyId:     dara.String(accessKeyID),
		AccessKeySecret: dara.String(accessKeySecret),
		RegionId:        dara.String(regionID),
	})
	if err != nil {
		return nil, err
	}
	return &DypnsClient{
		client:        client,
		schemeCode:    strings.TrimSpace(os.Getenv("DYPNS_FUSION_SCHEME_CODE")),
		packageName:   strings.TrimSpace(firstNonEmpty(os.Getenv("DYPNS_ANDROID_PACKAGE_NAME"), "com.nongjiqiancha")),
		packageSign:   strings.ToLower(strings.ReplaceAll(strings.TrimSpace(os.Getenv("DYPNS_ANDROID_PACKAGE_SIGN")), ":", "")),
		smsSignName:   strings.TrimSpace(os.Getenv("DYPNS_SMS_SIGN_NAME")),
		smsTemplate:   strings.TrimSpace(os.Getenv("DYPNS_SMS_TEMPLATE_CODE")),
		smsParam:      strings.TrimSpace(firstNonEmpty(os.Getenv("DYPNS_SMS_TEMPLATE_PARAM"), `{"code":"##code##"}`)),
		smsSchemeName: dypnsOptionalSMSSchemeName(),
	}, nil
}

func dypnsOptionalSMSSchemeName() string {
	return strings.TrimSpace(os.Getenv("DYPNS_SMS_SCHEME_NAME"))
}

func (c *DypnsClient) HasClientConfigured() bool {
	return c != nil && c.client != nil
}

func (c *DypnsClient) HasFusionConfigured() bool {
	return c.HasClientConfigured() && c.schemeCode != "" && c.packageName != "" && c.packageSign != ""
}

func (c *DypnsClient) HasSMSConfigured() bool {
	return c.HasClientConfigured() && c.smsSignName != "" && c.smsTemplate != "" && c.smsParam != ""
}

func (c *DypnsClient) FusionSchemeCode() string {
	if c == nil {
		return ""
	}
	return c.schemeCode
}

func (c *DypnsClient) GetFusionAuthToken(ctx context.Context) (string, error) {
	if !c.HasFusionConfigured() {
		return "", fmt.Errorf("fusion_auth_not_configured")
	}
	req := &dypnsapi.GetFusionAuthTokenRequest{}
	req.SetPlatform("Android")
	req.SetDurationSeconds(int64(30 * time.Minute / time.Second))
	req.SetSchemeCode(c.schemeCode)
	req.SetPackageName(c.packageName)
	req.SetPackageSign(c.packageSign)
	_ = ctx
	resp, err := c.client.GetFusionAuthTokenWithOptions(req, runtimeOptions())
	if err != nil {
		return "", err
	}
	if resp == nil || resp.Body == nil || !boolPtrValue(resp.Body.Success) || stringPtrValue(resp.Body.Code) != "OK" || stringPtrValue(resp.Body.Model) == "" {
		return "", fmt.Errorf("fusion_auth_token_failed:%s", stringPtrValue(resp.Body.GetMessage()))
	}
	return stringPtrValue(resp.Body.Model), nil
}

func (c *DypnsClient) VerifyFusionToken(ctx context.Context, verifyToken string) (string, error) {
	if !c.HasClientConfigured() {
		return "", fmt.Errorf("fusion_auth_not_configured")
	}
	verifyToken = strings.TrimSpace(verifyToken)
	if verifyToken == "" {
		return "", fmt.Errorf("verify_token_missing")
	}
	req := &dypnsapi.VerifyWithFusionAuthTokenRequest{}
	req.SetVerifyToken(verifyToken)
	_ = ctx
	resp, err := c.client.VerifyWithFusionAuthTokenWithOptions(req, runtimeOptions())
	if err != nil {
		return "", err
	}
	if resp == nil || resp.Body == nil || !boolPtrValue(resp.Body.Success) || stringPtrValue(resp.Body.Code) != "OK" || resp.Body.Model == nil {
		return "", fmt.Errorf("fusion_verify_failed:%s", stringPtrValue(resp.Body.GetMessage()))
	}
	if stringPtrValue(resp.Body.Model.VerifyResult) != "PASS" {
		return "", fmt.Errorf("fusion_verify_unknown")
	}
	phone := normalizeMainlandPhone(stringPtrValue(resp.Body.Model.PhoneNumber))
	if phone == "" {
		return "", fmt.Errorf("fusion_phone_missing")
	}
	return phone, nil
}

func (c *DypnsClient) SendSMSCode(ctx context.Context, phone string, outID string) error {
	if !c.HasSMSConfigured() {
		return fmt.Errorf("sms_auth_not_configured")
	}
	req := &dypnsapi.SendSmsVerifyCodeRequest{}
	req.SetPhoneNumber(phone)
	req.SetSignName(c.smsSignName)
	req.SetTemplateCode(c.smsTemplate)
	req.SetTemplateParam(c.smsParam)
	if c.smsSchemeName != "" {
		req.SetSchemeName(c.smsSchemeName)
	}
	req.SetCountryCode("86")
	req.SetCodeType(1)
	req.SetCodeLength(6)
	req.SetValidTime(300)
	req.SetDuplicatePolicy(1)
	req.SetReturnVerifyCode(false)
	if strings.TrimSpace(outID) != "" {
		req.SetOutId(outID)
	}
	_ = ctx
	resp, err := c.client.SendSmsVerifyCodeWithOptions(req, runtimeOptions())
	if err != nil {
		return err
	}
	if resp == nil || resp.Body == nil || !boolPtrValue(resp.Body.Success) || stringPtrValue(resp.Body.Code) != "OK" {
		return fmt.Errorf("sms_send_failed:%s", stringPtrValue(resp.Body.GetMessage()))
	}
	return nil
}

func (c *DypnsClient) CheckSMSCode(ctx context.Context, phone string, verifyCode string) error {
	if !c.HasClientConfigured() {
		return fmt.Errorf("sms_auth_not_configured")
	}
	req := &dypnsapi.CheckSmsVerifyCodeRequest{}
	req.SetPhoneNumber(phone)
	req.SetVerifyCode(strings.TrimSpace(verifyCode))
	req.SetCountryCode("86")
	if c.smsSchemeName != "" {
		req.SetSchemeName(c.smsSchemeName)
	}
	req.SetCaseAuthPolicy(1)
	_ = ctx
	resp, err := c.client.CheckSmsVerifyCodeWithOptions(req, runtimeOptions())
	if err != nil {
		return err
	}
	if resp == nil || resp.Body == nil || !boolPtrValue(resp.Body.Success) || stringPtrValue(resp.Body.Code) != "OK" || resp.Body.Model == nil {
		return fmt.Errorf("sms_check_failed:%s", stringPtrValue(resp.Body.GetMessage()))
	}
	if stringPtrValue(resp.Body.Model.VerifyResult) != "PASS" {
		return fmt.Errorf("sms_verify_unknown")
	}
	return nil
}

func runtimeOptions() *dara.RuntimeOptions {
	return &dara.RuntimeOptions{
		ConnectTimeout: dara.Int(5000),
		ReadTimeout:    dara.Int(10000),
		Autoretry:      dara.Bool(false),
		IgnoreSSL:      dara.Bool(false),
		BackoffPolicy:  dara.String("no"),
	}
}

func stringPtrValue(value *string) string {
	if value == nil {
		return ""
	}
	return strings.TrimSpace(*value)
}

func boolPtrValue(value *bool) bool {
	return value != nil && *value
}
