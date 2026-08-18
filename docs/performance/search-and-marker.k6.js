import http from 'k6/http';
import { check, sleep } from 'k6';

const baseUrl = __ENV.BASE_URL || 'http://localhost:8080';
const vus = Number(__ENV.VUS || 10);
const duration = __ENV.DURATION || '30s';
const boundsName = __ENV.BOUNDS || 'wide';
const searchQuery = encodeURIComponent(__ENV.SEARCH_QUERY || 'DbPerfPostgres Latte');

const boundsByName = {
  narrow: 'swLat=37.49&swLng=126.94&neLat=37.53&neLng=127.00',
  medium: 'swLat=37.45&swLng=126.90&neLat=37.57&neLng=127.05',
  wide: 'swLat=37.40&swLng=126.80&neLat=37.80&neLng=127.20',
};

const bounds = boundsByName[boundsName] || boundsByName.wide;
const markerStrategies = (__ENV.MARKER_STRATEGIES || 'UNSORTED_LIMIT,HOT_RANK_LIMIT,BATCH_METRIC_HOT_RANK,DB_HOT_RANK_LIMIT').split(',');
const searchStrategies = (__ENV.SEARCH_STRATEGIES || 'APP_CONTAINS,DB_LIKE,NORMALIZED').split(',');

export const options = {
  scenarios: {
    marker_load: {
      executor: 'constant-vus',
      exec: 'markers',
      vus,
      duration,
    },
    search_load: {
      executor: 'constant-vus',
      exec: 'search',
      vus,
      duration,
      startTime: __ENV.SEARCH_START || '0s',
    },
  },
  thresholds: {
    'http_req_failed': ['rate<0.01'],
    'http_req_duration{endpoint:markers,strategy:UNSORTED_LIMIT}': ['p(95)<60000'],
    'http_req_duration{endpoint:markers,strategy:HOT_RANK_LIMIT}': ['p(95)<60000'],
    'http_req_duration{endpoint:markers,strategy:BATCH_METRIC_HOT_RANK}': ['p(95)<60000'],
    'http_req_duration{endpoint:markers,strategy:DB_HOT_RANK_LIMIT}': ['p(95)<60000'],
    'http_req_duration{endpoint:search,strategy:APP_CONTAINS}': ['p(95)<60000'],
    'http_req_duration{endpoint:search,strategy:DB_LIKE}': ['p(95)<60000'],
    'http_req_duration{endpoint:search,strategy:NORMALIZED}': ['p(95)<60000'],
  },
  summaryTrendStats: ['min', 'avg', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

export function markers() {
  for (const strategy of markerStrategies) {
    const response = http.get(
      `${baseUrl}/api/v1/cafes/markers?${bounds}&zoom=13&strategy=${strategy}`,
      { tags: { endpoint: 'markers', strategy, bounds: boundsName } },
    );
    check(response, {
      [`markers ${strategy} 200`]: (res) => res.status === 200,
      [`markers ${strategy} has body`]: (res) => res.body && res.body.includes('"markers"'),
    });
  }
  sleep(Number(__ENV.SLEEP || 0.1));
}

export function search() {
  for (const strategy of searchStrategies) {
    const response = http.get(
      `${baseUrl}/api/v1/search/cafes?query=${searchQuery}&${bounds}&limit=100&strategy=${strategy}`,
      { tags: { endpoint: 'search', strategy, bounds: boundsName } },
    );
    check(response, {
      [`search ${strategy} 200`]: (res) => res.status === 200,
      [`search ${strategy} has body`]: (res) => res.body && res.body.includes('"items"'),
    });
  }
  sleep(Number(__ENV.SLEEP || 0.1));
}
