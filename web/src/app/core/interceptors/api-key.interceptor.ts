import { HttpInterceptorFn } from '@angular/common/http';
import { environment } from '../../../environments/environment';

export const apiKeyInterceptor: HttpInterceptorFn = (req, next) => {
  if (req.url.startsWith(environment.apiBaseUrl)) {
    const cloned = req.clone({
      setHeaders: { 'X-API-Key': environment.apiKey },
    });
    return next(cloned);
  }
  return next(req);
};
