import axios from 'axios';

const API_URL = 'http://localhost:3005/api/reports';
const ADMIN_API_URL = 'http://localhost:3005/admin';
const TOKEN_KEY = 'th_admin_token';

// Helpers for token
const setToken = (t) => { if (t) localStorage.setItem(TOKEN_KEY, t); };
const getToken = () => localStorage.getItem(TOKEN_KEY);
export const clearToken = () => localStorage.removeItem(TOKEN_KEY);

// ===== AUTH =====
export const loginAdmin = async (email, password) => {
  try {
    const response = await axios.post(`${ADMIN_API_URL}/login`, { email, password });
    const token = response?.data?.token;
    setToken(token);
    return response.data; // { success, token, ... }
  } catch (error) {
    return { success: false, message: error.response?.data?.message || 'Login failed' };
  }
};

// ===== REPORTS =====
export const fetchReports = async (token) => {
  try {
    const auth = token || getToken();
    const response = await axios.get(API_URL, {
      headers: { 'Authorization': `Bearer ${auth}` }
    });
    return response.data;
  } catch (error) {
    if (error?.response?.status === 401) clearToken();
    return { success: false, message: error.response?.data?.message || 'Failed to fetch reports' };
  }
};

export const fetchErrorsByDay = async (token) => {
  try {
    const auth = token || getToken();
    const response = await axios.get(`${API_URL}/statistics/errors-by-day`, {
      headers: { 'Authorization': `Bearer ${auth}` }
    });
    return response.data;
  } catch (error) {
    if (error?.response?.status === 401) clearToken();
    return { success: false, message: error.response?.data?.message || 'Failed to fetch errors by day' };
  }
};

export const fetchMostReported = async (token) => {
  try {
    const auth = token || getToken();
    const response = await axios.get(`${API_URL}/statistics/most-reported`, {
      headers: { 'Authorization': `Bearer ${auth}` }
    });
    return response.data;
  } catch (error) {
    if (error?.response?.status === 401) clearToken();
    return { success: false, message: error.response?.data?.message || 'Failed to fetch most reported keywords' };
  }
};

export const deleteReport = async (id, token) => {
  try {
    const auth = token || getToken();
    const response = await axios.delete(`${API_URL}/${id}`, {
      headers: { 'Authorization': `Bearer ${auth}` }
    });
    return response.data;
  } catch (error) {
    if (error?.response?.status === 401) clearToken();
    return { success: false, message: error.response?.data?.message || 'Failed to delete report' };
  }
};
