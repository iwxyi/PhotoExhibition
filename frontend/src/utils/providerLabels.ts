export const storageTypeLabel = (providerType?: string | null) => {
  switch (providerType) {
    case 'FTP':
      return 'FTP'
    case 'WEBDAV':
      return 'WebDAV'
    case 'COS':
      return '腾讯云 COS'
    case 'SFTP':
      return 'SFTP'
    case 'S3_COMPATIBLE':
      return 'S3 兼容'
    case 'MINIO':
      return 'MinIO'
    case 'OSS':
      return '阿里云 OSS'
    case 'R2':
      return 'Cloudflare R2'
    case 'SMB':
      return 'SMB 共享'
    case 'NFS':
      return 'NFS'
    case 'AZURE_BLOB':
      return 'Azure Blob'
    case 'GCS':
      return 'Google Cloud Storage'
    case 'OBS':
      return '华为云 OBS'
    case 'TOS':
      return '火山引擎 TOS'
    case 'BOS':
      return '百度云 BOS'
    case 'UCLOUD_US3':
      return 'UCloud US3'
    case 'JD_JSS':
      return '京东云 JSS'
    case 'WASABI':
      return 'Wasabi'
    case 'QINIU_KODO':
      return '七牛云 Kodo'
    case 'B2':
      return 'Backblaze B2'
    case 'UPYUN':
      return '又拍云'
    case 'DROPBOX':
      return 'Dropbox'
    case 'ONEDRIVE':
      return 'OneDrive'
    case 'LOCAL':
    default:
      return '本地存储'
  }
}

export const smsProviderLabel = (providerType?: string | null) => {
  switch (providerType) {
    case 'TENCENT_CLOUD':
      return '腾讯云'
    case 'TWILIO':
      return 'Twilio'
    case 'HUAWEI_CLOUD':
      return '华为云'
    case 'VOLCENGINE':
      return '火山引擎'
    case 'CLOOPEN':
      return '容联云通讯'
    case 'AWS_SNS':
      return 'AWS SNS'
    case 'YUNPIAN':
      return '云片'
    case 'SUBMAIL':
      return 'Submail'
    case 'MESSAGEBIRD':
      return 'MessageBird'
    case 'VONAGE':
      return 'Vonage'
    case 'INFOBIP':
      return 'Infobip'
    case 'PLIVO':
      return 'Plivo'
    case 'SINCH':
      return 'Sinch'
    case 'TELNYX':
      return 'Telnyx'
    case 'SMSAERO':
      return 'SMS Aero'
    case 'HTTP_WEBHOOK':
      return 'Webhook'
    case 'ALIYUN':
    default:
      return '阿里云'
  }
}

export const emailProviderLabel = (providerType?: string | null) => {
  switch (providerType) {
    case 'ALIYUN_DIRECTMAIL':
      return '阿里云邮件推送'
    case 'TENCENT_EXMAIL':
      return '腾讯企业邮'
    case 'AWS_SES':
      return 'AWS SES'
    case 'SENDGRID':
      return 'SendGrid'
    case 'MAILGUN':
      return 'Mailgun'
    case 'RESEND':
      return 'Resend'
    case 'POSTMARK':
      return 'Postmark'
    case 'BREVO':
      return 'Brevo'
    case 'MAILERSEND':
      return 'MailerSend'
    case 'ZEPTOMAIL':
      return 'ZeptoMail'
    case 'MAILJET':
      return 'Mailjet'
    case 'SPARKPOST':
      return 'SparkPost'
    case 'ELASTIC_EMAIL':
      return 'Elastic Email'
    case 'SMTP2GO':
      return 'SMTP2GO'
    case 'SENDLAYER':
      return 'SendLayer'
    case 'QQ_EXMAIL':
      return 'QQ 企业邮箱'
    case 'NETEASE_EXMAIL':
      return '网易企业邮箱'
    case 'CUSTOM_SMTP':
      return '自定义 SMTP'
    case 'SMTP':
    default:
      return 'SMTP'
  }
}

export const paymentProviderLabel = (providerType?: string | null) => {
  switch (providerType) {
    case 'WECHAT_PAY':
      return '微信支付'
    case 'STRIPE':
      return 'Stripe'
    case 'PAYPAL':
      return 'PayPal'
    case 'UNIONPAY':
      return '银联'
    case 'PADDLE':
      return 'Paddle'
    case 'LEMON_SQUEEZY':
      return 'Lemon Squeezy'
    case 'ADYEN':
      return 'Adyen'
    case 'MOLLIE':
      return 'Mollie'
    case 'XENDIT':
      return 'Xendit'
    case 'MIDTRANS':
      return 'Midtrans'
    case 'CUSTOM_WEBHOOK':
      return '自定义 Webhook'
    case 'ALIPAY':
    default:
      return '支付宝'
  }
}
