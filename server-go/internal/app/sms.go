package app

import (
	"context"
	"crypto/hmac"
	"crypto/rand"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"math/big"
	"os"
	"strings"
	"time"

	openapiutil "github.com/alibabacloud-go/darabonba-openapi/v2/utils"
	dysmsapi "github.com/alibabacloud-go/dysmsapi-20170525/v5/client"
	"github.com/alibabacloud-go/tea/dara"
)

const (
	defaultSMSCodeTTL    = 5 * time.Minute
	defaultSMSCodeLength = 6
)

type SMSClient struct {
	client       *dysmsapi.Client
	signName     string
	templateCode string
}

func NewSMSClientFromEnv() (*SMSClient, error) {
	accessKeyID := strings.TrimSpace(firstNonEmpty(
		os.Getenv("SMS_ACCESS_KEY_ID"),
		os.Getenv("DYSMS_ACCESS_KEY_ID"),
		os.Getenv("DYPNS_ACCESS_KEY_ID"),
		os.Getenv("ALIYUN_DYPNS_ACCESS_KEY_ID"),
		os.Getenv("ALIBABA_CLOUD_ACCESS_KEY_ID"),
	))
	accessKeySecret := strings.TrimSpace(firstNonEmpty(
		os.Getenv("SMS_ACCESS_KEY_SECRET"),
		os.Getenv("DYSMS_ACCESS_KEY_SECRET"),
		os.Getenv("DYPNS_ACCESS_KEY_SECRET"),
		os.Getenv("ALIYUN_DYPNS_ACCESS_KEY_SECRET"),
		os.Getenv("ALIBABA_CLOUD_ACCESS_KEY_SECRET"),
	))
	if accessKeyID == "" || accessKeySecret == "" {
		return &SMSClient{}, nil
	}
	regionID := strings.TrimSpace(firstNonEmpty(os.Getenv("SMS_REGION_ID"), os.Getenv("DYPNS_REGION_ID"), "cn-hangzhou"))
	client, err := dysmsapi.NewClient(&openapiutil.Config{
		AccessKeyId:     dara.String(accessKeyID),
		AccessKeySecret: dara.String(accessKeySecret),
		RegionId:        dara.String(regionID),
	})
	if err != nil {
		return nil, err
	}
	return &SMSClient{
		client:       client,
		signName:     strings.TrimSpace(firstNonEmpty(os.Getenv("SMS_SIGN_NAME"), os.Getenv("DYPNS_SMS_SIGN_NAME"))),
		templateCode: strings.TrimSpace(firstNonEmpty(os.Getenv("SMS_TEMPLATE_CODE"), os.Getenv("DYPNS_SMS_TEMPLATE_CODE"))),
	}, nil
}

func (c *SMSClient) HasConfigured() bool {
	return c != nil && c.client != nil && c.signName != "" && c.templateCode != ""
}

func (c *SMSClient) SendLoginCode(ctx context.Context, phone string, code string, outID string) error {
	if !c.HasConfigured() {
		return fmt.Errorf("sms_send_not_configured")
	}
	phone = normalizeMainlandPhone(phone)
	code = strings.TrimSpace(code)
	if phone == "" || code == "" {
		return fmt.Errorf("sms_invalid_request")
	}
	templateParam, err := json.Marshal(map[string]string{"code": code})
	if err != nil {
		return err
	}
	req := &dysmsapi.SendSmsRequest{}
	req.SetPhoneNumbers(phone)
	req.SetSignName(c.signName)
	req.SetTemplateCode(c.templateCode)
	req.SetTemplateParam(string(templateParam))
	if strings.TrimSpace(outID) != "" {
		req.SetOutId(outID)
	}
	_ = ctx
	resp, err := c.client.SendSmsWithOptions(req, runtimeOptions())
	if err != nil {
		return fmt.Errorf("sms_send_provider_error:%w", err)
	}
	if resp == nil || resp.Body == nil {
		return fmt.Errorf("sms_send_provider_failed:empty_response")
	}
	if stringPtrValue(resp.Body.GetCode()) != "OK" {
		return fmt.Errorf("sms_send_provider_failed:%s:%s", stringPtrValue(resp.Body.GetCode()), stringPtrValue(resp.Body.GetMessage()))
	}
	return nil
}

func randomSMSCode(length int) (string, error) {
	if length <= 0 {
		length = defaultSMSCodeLength
	}
	var builder strings.Builder
	builder.Grow(length)
	max := big.NewInt(10)
	for builder.Len() < length {
		n, err := rand.Int(rand.Reader, max)
		if err != nil {
			return "", err
		}
		builder.WriteByte(byte('0' + n.Int64()))
	}
	return builder.String(), nil
}

func smsCodeDigest(phone string, code string) string {
	phone = normalizeMainlandPhone(phone)
	code = strings.TrimSpace(code)
	if phone == "" || code == "" {
		return ""
	}
	secret := strings.TrimSpace(os.Getenv("APP_SECRET"))
	if secret == "" {
		sum := sha256.Sum256([]byte(phone + ":" + code))
		return hex.EncodeToString(sum[:])
	}
	mac := hmac.New(sha256.New, []byte(secret))
	_, _ = mac.Write([]byte(phone + ":" + code))
	return hex.EncodeToString(mac.Sum(nil))
}

func smsCodeCacheKey(phone string) string {
	phone = normalizeMainlandPhone(phone)
	if phone == "" {
		return ""
	}
	return "nj:auth:sms:code:" + rateLimitHash(phone, strings.TrimSpace(os.Getenv("APP_SECRET")))
}
