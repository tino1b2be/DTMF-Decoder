function [out,chars] = randSeq( numTones, Fs , amplitude)
% Funtion to generate a random sequence of length given number and Fs
    
    %start with a random pause
    out = genPause(randomInt(40,70),Fs);
    chars = zeros(1,numTones);
    for i = 1:numTones
        % add a random tone
        DTMF = randomInt(1,16);
        chars(i) = DTMF;
        tone = genDTMFtone(DTMF,Fs,randomInt(40,100),amplitude);
        % add a pause of random duration between 30 and 70
        pause = genPause(randomInt(30,100),Fs);
        % add to the output signal
        out = vertcat(out,tone,pause);
    end
    
end

