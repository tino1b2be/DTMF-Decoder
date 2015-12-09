function out = genPause( duration, Fs )
% Function to generate a random pause of a given duration
    samples = floor(duration/(1/Fs))/1000;
    out = zeros(samples,1);
    
end

