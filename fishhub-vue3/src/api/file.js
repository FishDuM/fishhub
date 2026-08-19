import axios from '@/axios'

const API_PREFIX = '/oss/file'

export function uploadFile(formData) {
  return axios.post(`${API_PREFIX}/upload`, formData, {
    headers: {
      'Content-Type': 'multipart/form-data'
    }
  })
}

/**
 * 申请 MinIO/OSS 直传预签名凭证
 */
export function getPresignedUrl(data) {
  return axios.post(`${API_PREFIX}/presigned-url`, data)
}

/**
 * 客户端直传 MinIO/OSS
 */
export async function uploadDirect(file) {
  const res = await getPresignedUrl({
    fileName: file.name,
    contentType: file.type || 'application/octet-stream'
  })
  if (!res.success || !res.data) {
    throw new Error(res.message || '获取上传凭证失败')
  }
  const { uploadUrl, downloadUrl } = res.data
  // 直接向 MinIO/OSS 发起 PUT 请求，绕过网关和微服务
  const response = await fetch(uploadUrl, {
    method: 'PUT',
    headers: {
      'Content-Type': file.type || 'application/octet-stream'
    },
    body: file
  })
  if (!response.ok) {
    throw new Error('文件直传失败')
  }
  return { success: true, data: downloadUrl }
}


