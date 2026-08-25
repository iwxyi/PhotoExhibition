import type { PaymentInitiationResponse } from '@/api'

function appendField(form: HTMLFormElement, key: string, value: unknown) {
  const input = document.createElement('input')
  input.type = 'hidden'
  input.name = key
  input.value = typeof value === 'string' ? value : JSON.stringify(value ?? '')
  form.appendChild(input)
}

export function canDirectLaunchPayment(initiation: PaymentInitiationResponse | null | undefined) {
  if (!initiation || initiation.mockMode) return false
  return initiation.actionType === 'REDIRECT_FORM'
    || initiation.actionType === 'REDIRECT_GET'
    || (initiation.actionType === 'QR_CODE' && !!initiation.qrCodeText)
}

export function launchPaymentInitiation(
  initiation: PaymentInitiationResponse,
  options?: { target?: '_self' | '_blank' }
) {
  if (!initiation?.launchUrl) {
    throw new Error('缺少支付发起地址')
  }
  const target = options?.target || '_self'

  if (initiation.actionType === 'REDIRECT_FORM') {
    const form = document.createElement('form')
    form.method = initiation.httpMethod || 'POST'
    form.action = initiation.launchUrl
    form.target = target
    form.style.display = 'none'
    Object.entries(initiation.formFields || {}).forEach(([key, value]) => appendField(form, key, value))
    document.body.appendChild(form)
    form.submit()
    document.body.removeChild(form)
    return
  }

  if (initiation.actionType === 'REDIRECT_GET') {
    const url = new URL(initiation.launchUrl, window.location.origin)
    Object.entries(initiation.formFields || {}).forEach(([key, value]) => {
      if (value == null) return
      url.searchParams.set(key, typeof value === 'string' ? value : JSON.stringify(value))
    })
    if (target === '_blank') {
      window.open(url.toString(), '_blank', 'noopener')
    } else {
      window.location.href = url.toString()
    }
    return
  }

  if (initiation.actionType === 'QR_CODE' && initiation.qrCodeText) {
    if (target === '_blank') {
      window.open(initiation.qrCodeText, '_blank', 'noopener')
    } else {
      window.location.href = initiation.qrCodeText
    }
    return
  }

  throw new Error('当前支付方式暂不支持浏览器直接拉起，请按预览参数继续联调')
}
