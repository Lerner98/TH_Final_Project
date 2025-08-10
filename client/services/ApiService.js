// services/ApiService.js
import axios from 'axios';
import Constants from '../utils/Constants';
import AsyncStorageUtils from '../utils/AsyncStorage';

const tag = (svc) => `[ApiService:${svc}]`;

const logReq = (svc, cfg) => {
  try {
    const base = cfg.baseURL || '';
    const url = cfg.url || '';
    const full = url.startsWith('http') ? url : `${base || ''}${url}`;
    console.log(`${tag(svc)} ${cfg.method?.toUpperCase()} ${full}`);
  } catch (e) {
    console.log(`${tag(svc)} REQ log error:`, e?.message || e);
  }
};

const logRes = (svc, res) => {
  try {
    const full = res?.config?.url?.startsWith('http')
      ? res.config.url
      : `${res?.config?.baseURL || ''}${res?.config?.url || ''}`;
    console.log(`${tag(svc)} ${res?.status} ${full}`);
  } catch (e) {
    console.log(`${tag(svc)} RES log error:`, e?.message || e);
  }
};

const logErr = (svc, err) => {
  try {
    const cfg = err?.config || {};
    const base = cfg.baseURL || '';
    const url = cfg.url || '';
    const full = url.startsWith('http') ? url : `${base || ''}${url}`;
    console.log(`${tag(svc)} ERR on ${cfg.method?.toUpperCase()} ${full}`);
    console.log(`${tag(svc)} ERR code:`, err?.code || 'n/a');
    console.log(`${tag(svc)} ERR message:`, err?.message || 'n/a');
    if (err?.response) {
      console.log(`${tag(svc)} ERR status:`, err.response.status);
      console.log(`${tag(svc)} ERR data preview:`, JSON.stringify(err.response.data || {}).slice(0, 400));
    }
  } catch (e) {
    console.log(`${tag(svc)} ERR log failure:`, e?.message || e);
  }
};

console.log('[ApiService] Service URLs:');
console.log('  Auth:', Constants.AUTH_API_URL);
console.log('  Translation:', Constants.TRANSLATION_API_URL);
console.log('  UserData:', Constants.USER_DATA_API_URL);

class ApiService {
  static instance;
  authInstance;
  translationInstance;
  userDataInstance;

  constructor() {
    // Create separate axios instances for each service
    this.authInstance = axios.create({
      baseURL: Constants.AUTH_API_URL,
      timeout: Constants.API_TIMEOUT,
    });

    this.translationInstance = axios.create({
      baseURL: Constants.TRANSLATION_API_URL,
      timeout: Constants.API_TIMEOUT,
    });

    this.userDataInstance = axios.create({
      baseURL: Constants.USER_DATA_API_URL,
      timeout: Constants.API_TIMEOUT,
    });

    // REQUEST interceptors (log everything)
    this.authInstance.interceptors.request.use((cfg) => { logReq('AUTH', cfg); return cfg; });
    this.translationInstance.interceptors.request.use((cfg) => { logReq('TRANS', cfg); return cfg; });
    this.userDataInstance.interceptors.request.use((cfg) => { logReq('UDS', cfg); return cfg; });

    // RESPONSE interceptors (log + handle 401/403)
    const responseHandler = (svc) => (response) => {
      logRes(svc, response);
      return response;
    };
    const errorHandler = (svc) => async (error) => {
      logErr(svc, error);
      const status = error?.response?.status;
      if (status === 401 || status === 403) {
        await AsyncStorageUtils.removeItem('user');
        await AsyncStorageUtils.removeItem('signed_session_id');
        await AsyncStorageUtils.removeItem('preferences');
      }
      return Promise.reject(error);
    };

    this.authInstance.interceptors.response.use(responseHandler('AUTH'), errorHandler('AUTH'));
    this.translationInstance.interceptors.response.use(responseHandler('TRANS'), errorHandler('TRANS'));
    this.userDataInstance.interceptors.response.use(responseHandler('UDS'), errorHandler('UDS'));
  }

  /**
   * Get the singleton instance of ApiService.
   */
  static getInstance() {
    if (!ApiService.instance) {
      ApiService.instance = new ApiService();
    }
    return ApiService.instance;
  }

  /**
   * Choose axios instance by endpoint path.
   */
  getAxiosInstance(url) {
    // Auth endpoints
    if (
      url.includes('/register') ||
      url.includes('/login') ||
      url.includes('/logout') ||
      url.includes('/validate-session') ||
      url.includes('/auth/')
    ) {
      return this.authInstance;
    }

    // Translation endpoints
    if (
      url.includes('/translate') ||
      url.includes('/recognize') ||
      url.includes('/speech') ||
      url.includes('/text-to-speech') ||
      url.includes('/asl') ||
      url.includes('/extract') ||
      url.includes('/generate') ||
      url.includes('/languages')
    ) {
      return this.translationInstance;
    }

    // User data endpoints
    if (
      url.includes('/preferences') ||
      url.includes('/translations') ||
      url.includes('/statistics') ||
      url.includes('/audit-logs')
    ) {
      return this.userDataInstance;
    }

    console.warn(`[ApiService] Unknown endpoint: ${url}, defaulting to AUTH service`);
    return this.authInstance;
  }

  /**
   * GET
   */
  async get(url, token = null, config = {}) {
    try {
      const axiosInstance = this.getAxiosInstance(url);
      const headers = {
        ...(token && { Authorization: `Bearer ${token}` }),
        ...(config.headers || {}),
      };
      const response = await axiosInstance.get(url, {
        ...config,
        headers,
      });
      return { success: true, data: response.data };
    } catch (error) {
      return this.handleError('GET', url, error);
    }
  }

  /**
   * POST
   */
  async post(url, data, token = null, config = {}) {
    try {
      const axiosInstance = this.getAxiosInstance(url);
      const isFormData = typeof FormData !== 'undefined' && data instanceof FormData;

      const headers = {
        ...(token && { Authorization: `Bearer ${token}` }),
        ...(isFormData ? {} : { 'Content-Type': 'application/json' }),
        ...(config.headers || {}),
      };

      const response = await axiosInstance.post(url, data, {
        ...config,
        headers,
      });

      return { success: true, data: response.data };
    } catch (error) {
      return this.handleError('POST', url, error);
    }
  }

  /**
   * DELETE
   */
  async delete(url, token = null, config = {}) {
    try {
      const axiosInstance = this.getAxiosInstance(url);
      const headers = {
        ...(token && { Authorization: `Bearer ${token}` }),
        ...(config.headers || {}),
      };
      const response = await axiosInstance.delete(url, {
        ...config,
        headers,
      });
      return { success: true, data: response.data };
    } catch (error) {
      return this.handleError('DELETE', url, error);
    }
  }

  /**
   * Unified error handler with richer diagnostics
   */
  handleError(method, url, error) {
    // Network-level (no response)
    if (error.request && !error.response) {
      const code = error.code || 'NO_CODE';
      const msg = error.message || 'Network error';
      console.log(`[ApiService] ${method} ${url} NETWORK ERROR:`, code, msg);
      return {
        success: false,
        error: `Network error - ${code}: ${msg}`,
      };
    }

    // HTTP response error
    if (error.response) {
      const status = error.response.status;
      const payload = error.response.data?.error || JSON.stringify(error.response.data || {});
      console.log(`[ApiService] ${method} ${url} HTTP ${status}:`, payload);
      return {
        success: false,
        error: payload || 'API request failed',
      };
    }

    // Something else
    return {
      success: false,
      error: error.message || 'Request setup error',
    };
  }
}

export default ApiService.getInstance();