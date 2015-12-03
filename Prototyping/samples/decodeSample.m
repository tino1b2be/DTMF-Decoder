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
%Created: 2015-12-02

function output = decodeSample( frame, Fs )
% Function that decodes a given frame and gives an output in the form a
% vector with first element being the NUMBER being represented, and the
% rest of the elements being the magnitudes of the each of the DTMF signals
% in the frame.

    output = zeros(1,9);                                                    % initialise the return value
    f_bin = [697,770,582,941,1209,1336,1477,1633];                          % frequency bin for the Goertzel calculation
    indices = round(f_bin/Fs * length(frame)) + 1;                          % comput indices for the Goertzel calculation
    output(2:end) = abs(goertzel(frame,indices));                           % magnitudes are set
    
end
