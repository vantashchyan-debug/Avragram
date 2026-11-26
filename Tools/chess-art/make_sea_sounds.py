import numpy as np, wave, os, subprocess, sys
R=44100
OUT=sys.argv[1]; os.makedirs(OUT,exist_ok=True)
rng=np.random.default_rng(11)
def t(sec): return np.arange(int(R*sec))/R
def lp(y,a):
    out=np.empty_like(y); s=0.0
    for i,v in enumerate(y): s+=a*(v-s); out[i]=s
    return out
def hp(y): return np.diff(y,prepend=0)
def env(n,att,dec):
    x=np.arange(n)/R; e=np.exp(-x/dec); a=int(att*R)
    if a>0: e[:a]*=np.linspace(0,1,a)
    return e
def noise(sec): return rng.standard_normal(int(R*sec))
def place(total,*parts):
    y=np.zeros(int(R*total))
    for off,s in parts:
        i=int(R*off); n=min(len(s),len(y)-i); y[i:i+n]+=s[:n]
    return y
def sweep(f0,f1,sec,dec):
    x=t(sec); f=np.linspace(f0,f1,len(x)); ph=2*np.pi*np.cumsum(f)/R
    return np.sin(ph)*np.exp(-x/dec)
def boom(sec=1.2, size=1.0):
    n=noise(sec); low=lp(lp(n,0.02*size+0.01),0.04)*env(len(n),0.004,0.25*size)
    crack=lp(n,0.5)*env(len(n),0.0005,0.03)
    thump=sweep(90,38,sec,0.18*size)
    y=low*6+crack*0.35+thump*0.9
    return y
def splash():
    n=noise(0.9)
    body=lp(hp(n),0.35)*env(len(n),0.01,0.12)
    spray=hp(hp(n))*env(len(n),0.03,0.22)*0.25
    plop=sweep(420,140,0.25,0.06)*0.5
    return place(0.9,(0,plop),(0.01,body),(0.04,spray))
def whoosh():
    n=noise(0.45); x=t(0.45)
    e=np.sin(np.pi*np.clip(x/0.45,0,1))**2
    return lp(hp(n),0.25)*e*0.8+sweep(900,300,0.45,0.3)*0.08
def bar(f,dur=0.8,amp=1,dec=0.3):
    x=t(dur); y=np.zeros_like(x)
    for m,a,d in [(1,1,dec),(3.93,0.3,dec*0.3),(9.2,0.1,dec*0.1)]: y+=a*np.sin(2*np.pi*f*m*x)*np.exp(-x/d)
    a=int(0.003*R); y[:a]*=np.linspace(0,1,a)
    return y*amp
def brass(f,dur):
    x=t(dur); y=np.zeros_like(x)
    for h in range(1,8): y+=np.sin(2*np.pi*f*h*x)/(h**1.2)
    e=np.minimum(1,x/0.03)*np.exp(-np.maximum(0,x-dur*0.7)/0.08)
    return lp(y*e,0.25)
def clunk():
    x=t(0.25)
    m=sum(a*np.sin(2*np.pi*f*x)*np.exp(-x/d) for f,a,d in [(180,0.6,0.05),(520,0.5,0.03),(1340,0.3,0.015),(2900,0.15,0.008)])
    return m+lp(noise(0.25),0.6)*env(int(R*0.25),0,0.004)*0.3
S={
 'shot': place(0.6,(0,boom(0.5,0.35)*0.5),(0.06,whoosh())),
 'splash': place(1.0,(0,whoosh()*0.6),(0.22,splash())),
 'hit': place(1.4,(0,whoosh()*0.5),(0.2,boom(1.1,1.0))),
 'sunk': place(2.2,(0,whoosh()*0.4),(0.18,boom(1.3,1.3)),(0.55,boom(1.4,1.6)*0.8),(0.6,lp(noise(1.5),0.01)*env(int(R*1.5),0.2,0.6)*3)),
 'place': clunk(),
 'start': place(1.2,(0,brass(392,0.25)),(0.22,brass(523.25,0.25)),(0.44,brass(659.25,0.6))),
 'win': place(2.0,(0,brass(523.25,0.22)),(0.2,brass(659.25,0.22)),(0.4,brass(783.99,0.22)),(0.62,brass(1046.5,0.9)),(0.62,bar(523.25,1.2,0.4,0.5))),
 'lose': place(1.8,(0,brass(392,0.35)),(0.32,brass(369.99,0.35)),(0.64,brass(349.23,0.35)),(0.96,brass(329.63,0.8))),
 'turn': place(0.6,(0,bar(880,0.4,0.5,0.15)),(0.09,bar(1318.5,0.4,0.4,0.15))),
}
for k,y in S.items():
    y=y/np.max(np.abs(y))*0.88
    n=int(R*0.03); y[-n:]*=np.linspace(1,0,n)
    w=os.path.join(OUT,'_t.wav')
    with wave.open(w,'wb') as f:
        f.setnchannels(1); f.setsampwidth(2); f.setframerate(R); f.writeframes((y*32767).astype('<i2').tobytes())
    subprocess.check_call(['ffmpeg','-loglevel','error','-y','-i',w,'-c:a','libvorbis','-q:a','4',os.path.join(OUT,'sea_%s.ogg'%k)])
os.remove(os.path.join(OUT,'_t.wav')); print('ok')
