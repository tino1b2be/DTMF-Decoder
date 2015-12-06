% Copyright (C) 2015 Tinotenda Chemvura
% 
% This program is free software; you can redistribute it and/or modify it
% under the terms of the GNU General Public License as published by
% the Free Software Foundation; either version 3 of the License, or
% (at your option) any later version.
% 
% This program is distributed in the hope that it will be useful,
% but WITHOUT ANY WARRANTY; without even the implied warranty of
% MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
% GNU General Public License for more details.
% 
% You should have received a copy of the GNU General Public License
% along with this program.  If not, see <http://www.gnu.org/licenses/>.

% -*- texinfo -*- 
% @deftypefn {Function File} {@var{retval} =} makeFrames (@var{input1}, @var{input2})
%
% @seealso{}
% @end deftypefn

%Author: Tinotenda Chemvura @tino1b2be
%Created: 2015-12-03

function dft_data  = transformFrames( frames, Fs )
% Function that decodes a given frame matrix and gives an output in the form a
% matrix with each column giving the magnitudes of the each of the DTMF 
% signals in the corresponding frame.

    dft_data = zeros(8,size(frames,2));                                            % initialise the output matrix
    f_bin = [697,770,852,941,1209,1336,1477,1633];                          % frequency bin for the Goertzel calculation
    %f_bin = [696:699,769:771,851:853,940:942,1208:1210,1335:1337,1476:1478,1632:16234];
    indices = round(f_bin/Fs * length(frames)) + 1;                          % comput indices for the Goertzel calculation
    
    for i = 1:size(frames,2)
        dft_data(:,i) = abs(goertzel(frames(:,i),indices)); 
    end                                   
end
