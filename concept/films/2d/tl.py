NAMES = ['intro', 'title', 'premise', 'quiet', 'profiles', 'search', 'voice', 'scenes', 'art', 'lights',
         'doorbell', 'speakers', 'bedroom', 'phone', 'games', 'rooms', 'end']
DURS = [4.0, 4.5, 6.5, 14.0, 24.0, 24.0, 22.0, 28.0, 24.0, 24.0, 20.0, 22.0, 20.0, 24.0, 22.0, 11.0, 6.0]
OVER = 0.4
START = {}
_t = 0.0
for _n, _d in zip(NAMES, DURS):
    START[_n] = _t
    _t += _d
TOTAL = _t

def span(name):
    i = NAMES.index(name)
    s = START[name]
    e = TOTAL if i == len(NAMES) - 1 else s + DURS[i] + OVER
    return s, e

def A(name, local):
    return START[name] + local
