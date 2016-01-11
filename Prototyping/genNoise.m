function [ noise ] = genNoise( duration, Fs, amplitude )
% Function to generate white noise, duration in ms.

    len = floor(duration*Fs/1000);
    noise = randn(len,1);
    % clip the signal to +/-1
    for i = 1:len
        if (noise(i) >= amplitude)
            noise(i) = amplitude;
        elseif (noise(i) <= -amplitude)
            noise(i) = -amplitude;
        end
    end
end

