import numpy as np, wave, os, subprocess, sys
R=44100
OUT=sys.argv[1]; os.makedirs(OUT,exist_ok=True)
rng=np.random.default_rng(7)
def t(sec): return np.arange(int(R*sec))/R
def modes(spec, dur, f_jit=0.0):
    x=t(dur); y=np.zeros_like(x)
    for f,a,dec in spec:
        f*=1+rng.uniform(-f_jit,f_jit)
        y+=a*np.sin(2*np.pi*f*x+rng.uniform(0,6.28))*np.exp(-x/dec)
    return y
def noise_burst(dur, dec, hp=True):
    x=t(dur); n=rng.standard_normal(len(x))
    if hp: n=np.diff(n,prepend=0)
    return n*np.exp(-x/dec)
def lowpass(y,a):
    out=np.zeros_like(y); s=0
    for i,v in enumerate(y):
        s+=a*(v-s); out[i]=s
    return out
def attack(y, ms=0.6):
    n=int(R*ms/1000); env=np.ones_like(y); env[:n]=np.linspace(0,1,n); return y*env
def room(y, length=0.09, mix=0.18):
    ir_t=t(length); ir=rng.standard_normal(len(ir_t))*np.exp(-ir_t/0.018)
    ir=lowpass(ir,0.35); ir/=np.abs(ir).sum()/6
    dry=np.concatenate([y,np.zeros(len(ir_t))]); wet=np.convolve(y,ir); wet=np.pad(wet,(0,max(0,len(dry)-len(wet))))[:len(dry)]
    return dry+mix*wet
def place(total, *parts):
    y=np.zeros(int(R*total))
    for off,s in parts:
        i=int(R*off); n=min(len(s),len(y)-i); y[i:i+n]+=s[:n]
    return y
def wood_thock(hard=1.0, pitch=1.0):
    body=modes([(150*pitch,0.55,0.030),(420*pitch,0.5,0.022),(890*pitch,0.38,0.016),(1480*pitch,0.26,0.010),(2350*pitch,0.16,0.007),(3600*pitch,0.08,0.004)],0.16,0.03)
    click=lowpass(noise_burst(0.02,0.0012),0.55)*0.35*hard
    return attack(body+place(0.16,(0,click)))
def wood_clack(pitch=1.0):
    return attack(modes([(1750*pitch,0.5,0.009),(2700*pitch,0.4,0.007),(3900*pitch,0.25,0.005),(980*pitch,0.3,0.012)],0.08,0.03)+place(0.08,(0,lowpass(noise_burst(0.01,0.0008),0.7)*0.3)))
def marble(pitch=1.0):
    return attack(modes([(520*pitch,0.35,0.05),(1330*pitch,0.4,0.035),(2610*pitch,0.35,0.03),(4020*pitch,0.22,0.022),(5900*pitch,0.12,0.015)],0.3,0.01)+place(0.3,(0,noise_burst(0.01,0.0007)*0.22)))
def bar(f, dur=0.9, amp=1.0, dec=0.35):  # marimba-like bar
    return attack(amp*modes([(f,1.0,dec),(f*3.93,0.32,dec*0.28),(f*9.24,0.10,dec*0.12)],dur),3)
def bell(f,dur=1.0,amp=1.0):
    return attack(amp*modes([(f,1,0.4),(f*2.0,0.35,0.25),(f*2.76,0.25,0.18),(f*5.4,0.1,0.08)],dur),2)
N={'C5':523.25,'D5':587.33,'E5':659.25,'G5':783.99,'A5':880,'C6':1046.5,'E6':1318.5,'G4':392,'C4':261.63,'E4':329.63}
def norm(y,peak=0.85):
    return y/np.max(np.abs(y))*peak
S={}
# ---- wood: realistic board
S['wood']={
 'move': room(wood_thock()),
 'capture': room(place(0.2,(0,wood_clack()*0.8),(0.035,wood_thock(1.4,0.95)*1.1))),
 'check': room(place(0.45,(0,wood_thock()),(0.05,bell(N['E6'],0.4,0.18)))),
 'castle': room(place(0.25,(0,wood_thock(1,1.02)),(0.085,wood_thock(1,0.96)*0.9))),
 'start': room(place(0.8,(0,bar(N['C5'],0.6,0.5)),(0.09,bar(N['E5'],0.6,0.5)),(0.18,bar(N['G5'],0.6,0.6)))),
 'end': room(place(1.1,(0,bar(N['G5'],0.8,0.5)),(0.12,bar(N['E5'],0.8,0.5)),(0.24,bar(N['C5'],0.9,0.6)),(0.24,bar(N['C4'],0.9,0.35)))),
 'lowtime': room(place(0.25,(0,wood_clack(1.3)*0.7),(0.12,wood_clack(1.1)*0.6))),
 'notify': room(place(0.5,(0,bar(N['A5'],0.4,0.4)),(0.1,bar(N['E6'],0.4,0.35)))),
}
S['marble']={
 'move': room(marble(),0.12,0.25),
 'capture': room(place(0.35,(0,marble(1.25)*0.7),(0.03,marble(0.9))),0.12,0.25),
 'check': room(place(0.6,(0,marble()),(0.04,bell(N['G5'],0.5,0.3)),(0.04,bell(N['C6'],0.5,0.2))),0.12,0.25),
 'castle': room(place(0.4,(0,marble(1.03)),(0.09,marble(0.95)*0.85)),0.12,0.25),
 'start': room(place(1.0,(0,bell(N['C5'],0.8,0.5)),(0.1,bell(N['G5'],0.8,0.5)),(0.2,bell(N['C6'],0.8,0.45))),0.12,0.25),
 'end': room(place(1.3,(0,bell(N['C6'],1,0.4)),(0.13,bell(N['G5'],1,0.45)),(0.26,bell(N['C5'],1,0.55))),0.12,0.25),
 'lowtime': room(place(0.3,(0,marble(1.6)*0.5),(0.13,marble(1.45)*0.45)),0.12,0.25),
 'notify': room(place(0.7,(0,bell(N['E6'],0.6,0.35)),(0.11,bell(N['C6'],0.6,0.3))),0.12,0.25),
}
S['marimba']={
 'move': room(bar(N['G4']*1.0,0.35,1,0.16)),
 'capture': room(place(0.45,(0,bar(N['C5'],0.35,0.8,0.14)),(0.06,bar(N['G4'],0.4,1,0.16)))),
 'check': room(place(0.6,(0,bar(N['E5'],0.5,0.9,0.2)),(0.07,bar(N['A5'],0.5,0.8,0.2)))),
 'castle': room(place(0.5,(0,bar(N['C5'],0.35,0.8,0.15)),(0.08,bar(N['E5'],0.4,0.8,0.15)))),
 'start': room(place(0.9,(0,bar(N['C5'])),(0.08,bar(N['E5'])),(0.16,bar(N['G5'])),(0.24,bar(N['C6'],0.6,0.8)))),
 'end': room(place(1.2,(0,bar(N['C6'])),(0.1,bar(N['G5'])),(0.2,bar(N['E5'])),(0.3,bar(N['C5'],0.9,1.1)))),
 'lowtime': room(place(0.3,(0,bar(N['C6'],0.15,0.6,0.05)),(0.12,bar(N['C6'],0.15,0.5,0.05)))),
 'notify': room(place(0.6,(0,bar(N['G5'],0.4,0.8)),(0.1,bar(N['C6'],0.4,0.8)))),
}
for style,d in S.items():
    for ev,y in d.items():
        y=norm(y, 0.9 if ev in('move','capture','castle') else 0.75)
        # gentle tail fade
        n=int(R*0.02); y[-n:]*=np.linspace(1,0,n)
        wav=os.path.join(OUT,'_t.wav')
        with wave.open(wav,'wb') as w:
            w.setnchannels(1); w.setsampwidth(2); w.setframerate(R)
            w.writeframes((y*32767).astype('<i2').tobytes())
        subprocess.check_call(['ffmpeg','-loglevel','error','-y','-i',wav,'-c:a','libvorbis','-q:a','5',os.path.join(OUT,'chess_%s_%s.ogg'%(style,ev))])
os.remove(os.path.join(OUT,'_t.wav'))
print('ok')
